package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PreparedMediaResult(
  val mediaType: MediaType,
  val mediaUrl: String,
  val mediaThumbnail: String,
  val mediaName: String,
  val mediaSize: Long,
  val mediaDuration: Int,
  val localUri: String,
  val localFilePath: String = "",
  val totalChunks: Int = 0
)

sealed class VideoProcessResult {
  data class Success(val prepared: PreparedMediaResult) : VideoProcessResult()
  data class ExceedsDuration(val durationSeconds: Int) : VideoProcessResult()
  data class Error(val message: String) : VideoProcessResult()
}

object MediaUtils {

  private const val PREFS_MEDIA_CACHE = "maxogram_media_cache"
  const val CHUNK_SIZE_BYTES = 450 * 1024 // 450 KB chunks comfortably within Firestore 1MB doc limit

  fun getLocalCachedMediaUri(context: Context, messageId: String): String? {
    val prefs = context.getSharedPreferences(PREFS_MEDIA_CACHE, Context.MODE_PRIVATE)
    val path = prefs.getString("msg_$messageId", null) ?: return null
    val file = File(path)
    return if (file.exists() && file.length() > 0) Uri.fromFile(file).toString() else null
  }

  fun getLocalCachedFile(context: Context, messageId: String): File? {
    val prefs = context.getSharedPreferences(PREFS_MEDIA_CACHE, Context.MODE_PRIVATE)
    val path = prefs.getString("msg_$messageId", null) ?: return null
    val file = File(path)
    return if (file.exists() && file.length() > 0) file else null
  }

  fun saveLocalMediaCacheMapping(context: Context, messageId: String, localPathOrUri: String) {
    context.getSharedPreferences(PREFS_MEDIA_CACHE, Context.MODE_PRIVATE)
      .edit()
      .putString("msg_$messageId", localPathOrUri)
      .apply()
  }

  suspend fun cacheRemoteMediaLocally(
    context: Context,
    messageId: String,
    mediaType: MediaType,
    mediaData: String
  ): String? = withContext(Dispatchers.IO) {
    if (mediaData.isBlank()) return@withContext null
    try {
      val mediaDir = File(context.filesDir, "maxogram_media").apply { mkdirs() }
      val extension = when (mediaType) {
        MediaType.PHOTO -> "jpg"
        MediaType.VIDEO -> "mp4"
        else -> "bin"
      }
      val targetFile = File(mediaDir, "media_${messageId}.$extension")
      if (targetFile.exists() && targetFile.length() > 0) {
        val uriStr = Uri.fromFile(targetFile).toString()
        saveLocalMediaCacheMapping(context, messageId, targetFile.absolutePath)
        return@withContext uriStr
      }

      val rawBase64 = if (mediaData.contains(",")) mediaData.substringAfter(",") else mediaData
      val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
      FileOutputStream(targetFile).use { it.write(bytes) }

      saveLocalMediaCacheMapping(context, messageId, targetFile.absolutePath)
      Uri.fromFile(targetFile).toString()
    } catch (e: Exception) {
      null
    }
  }

  fun splitFileIntoChunks(file: File, chunkSize: Int = CHUNK_SIZE_BYTES): List<String> {
    if (!file.exists() || file.length() == 0L) return emptyList()
    val chunks = mutableListOf<String>()
    val buffer = ByteArray(chunkSize)
    file.inputStream().use { input ->
      var read: Int
      while (input.read(buffer).also { read = it } != -1) {
        val chunkBytes = if (read == chunkSize) buffer else buffer.copyOf(read)
        chunks.add(Base64.encodeToString(chunkBytes, Base64.NO_WRAP))
      }
    }
    return chunks
  }

  fun assembleChunksToFile(chunks: List<String>, targetFile: File) {
    targetFile.parentFile?.mkdirs()
    targetFile.outputStream().use { output ->
      for (chunk in chunks) {
        val bytes = Base64.decode(chunk, Base64.DEFAULT)
        output.write(bytes)
      }
    }
  }

  suspend fun processPhotoUri(context: Context, uri: Uri): PreparedMediaResult? = withContext(Dispatchers.IO) {
    try {
      val resolver = context.contentResolver
      val inputStream = resolver.openInputStream(uri) ?: return@withContext null

      // Check bounds
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeStream(inputStream, null, options)
      inputStream.close()

      val origW = options.outWidth
      val origH = options.outHeight
      if (origW <= 0 || origH <= 0) return@withContext null

      val maxDimension = 960
      var inSampleSize = 1
      while (origW / inSampleSize > maxDimension || origH / inSampleSize > maxDimension) {
        inSampleSize *= 2
      }

      val decodeStream = resolver.openInputStream(uri) ?: return@withContext null
      val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
      }
      val sampledBitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
      decodeStream.close()
      if (sampledBitmap == null) return@withContext null

      // Scale to fit maxDimension
      val ratio = Math.min(maxDimension.toFloat() / sampledBitmap.width, maxDimension.toFloat() / sampledBitmap.height)
      val finalW = if (ratio < 1.0f) (sampledBitmap.width * ratio).toInt() else sampledBitmap.width
      val finalH = if (ratio < 1.0f) (sampledBitmap.height * ratio).toInt() else sampledBitmap.height

      val scaledBitmap = if (finalW != sampledBitmap.width || finalH != sampledBitmap.height) {
        Bitmap.createScaledBitmap(sampledBitmap, finalW, finalH, true).also {
          if (it != sampledBitmap) sampledBitmap.recycle()
        }
      } else {
        sampledBitmap
      }

      // Main image JPEG Base64
      val mainOut = ByteArrayOutputStream()
      scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 72, mainOut)
      val mainBytes = mainOut.toByteArray()
      val base64Data = "data:image/jpeg;base64," + Base64.encodeToString(mainBytes, Base64.NO_WRAP)

      // Micro thumbnail (~64px)
      val thumbW = 64
      val thumbH = ((64f / finalW) * finalH).toInt().coerceAtLeast(32)
      val thumbBitmap = Bitmap.createScaledBitmap(scaledBitmap, thumbW, thumbH, true)
      val thumbOut = ByteArrayOutputStream()
      thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 50, thumbOut)
      val thumbBytes = thumbOut.toByteArray()
      val base64Thumb = "data:image/jpeg;base64," + Base64.encodeToString(thumbBytes, Base64.NO_WRAP)
      thumbBitmap.recycle()
      scaledBitmap.recycle()

      val mediaDir = File(context.filesDir, "maxogram_media").apply { mkdirs() }
      val fileName = "photo_${System.currentTimeMillis()}.jpg"
      val localFile = File(mediaDir, fileName)
      FileOutputStream(localFile).use { it.write(mainBytes) }
      val localUriStr = Uri.fromFile(localFile).toString()

      PreparedMediaResult(
        mediaType = MediaType.PHOTO,
        mediaUrl = base64Data,
        mediaThumbnail = base64Thumb,
        mediaName = fileName,
        mediaSize = mainBytes.size.toLong(),
        mediaDuration = 0,
        localUri = localUriStr,
        localFilePath = localFile.absolutePath,
        totalChunks = 0
      )
    } catch (e: Exception) {
      null
    }
  }

  suspend fun processVideoUri(context: Context, uri: Uri): VideoProcessResult = withContext(Dispatchers.IO) {
    try {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(context, uri)
      } catch (e: Exception) {
        return@withContext VideoProcessResult.Error("Не удалось прочитать видеофайл")
      }

      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      val durationMs = durationStr?.toLongOrNull() ?: 0L
      val durationSec = (durationMs / 1000).toInt()

      // 1 minute limiter enforcement
      if (durationSec > 60) {
        try { retriever.release() } catch (_: Exception) {}
        return@withContext VideoProcessResult.ExceedsDuration(durationSec)
      }

      val frameBitmap = retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        ?: retriever.frameAtTime

      val thumbBase64 = if (frameBitmap != null) {
        val maxThumbDim = 320
        val scale = Math.min(maxThumbDim.toFloat() / frameBitmap.width, maxThumbDim.toFloat() / frameBitmap.height)
        val tw = if (scale < 1.0f) (frameBitmap.width * scale).toInt() else frameBitmap.width
        val th = if (scale < 1.0f) (frameBitmap.height * scale).toInt() else frameBitmap.height
        val scaled = Bitmap.createScaledBitmap(frameBitmap, tw, th, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 65, out)
        val bytes = out.toByteArray()
        scaled.recycle()
        if (scaled != frameBitmap) frameBitmap.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
      } else {
        ""
      }

      try { retriever.release() } catch (_: Exception) {}

      // Copy video to local permanent app media storage
      val mediaDir = File(context.filesDir, "maxogram_media").apply { mkdirs() }
      val fileName = "video_${System.currentTimeMillis()}.mp4"
      val localFile = File(mediaDir, fileName)

      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(localFile).use { output ->
          input.copyTo(output)
        }
      } ?: return@withContext VideoProcessResult.Error("Не удалось скопировать видеофайл")

      val fileSize = localFile.length()
      val localUriStr = Uri.fromFile(localFile).toString()

      // If video fits within ~600KB Base64 limit, store directly in mediaUrl.
      // Otherwise, chunks will be uploaded with progress tracking.
      val (mediaUrlPayload, totalChunks) = if (fileSize in 1..(600 * 1024)) {
        val bytes = localFile.readBytes()
        Pair("data:video/mp4;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP), 0)
      } else {
        val chunksCount = ((fileSize + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).toInt()
        Pair("", chunksCount)
      }

      VideoProcessResult.Success(
        PreparedMediaResult(
          mediaType = MediaType.VIDEO,
          mediaUrl = mediaUrlPayload,
          mediaThumbnail = thumbBase64,
          mediaName = fileName,
          mediaSize = fileSize,
          mediaDuration = durationSec,
          localUri = localUriStr,
          localFilePath = localFile.absolutePath,
          totalChunks = totalChunks
        )
      )
    } catch (e: Exception) {
      VideoProcessResult.Error(e.message ?: "Ошибка при обработке видео")
    }
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
      mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
      kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
      else -> "$bytes B"
    }
  }

  fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", m, s)
  }
}
