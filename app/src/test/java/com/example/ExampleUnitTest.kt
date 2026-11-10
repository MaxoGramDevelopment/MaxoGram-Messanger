package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testMediaUtilsFormatDuration() {
    assertEquals("0:45", com.example.util.MediaUtils.formatDuration(45))
    assertEquals("1:05", com.example.util.MediaUtils.formatDuration(65))
  }

  @Test
  fun testMediaUtilsFormatFileSize() {
    assertEquals("500 B", com.example.util.MediaUtils.formatFileSize(500))
    assertEquals("2.0 MB", com.example.util.MediaUtils.formatFileSize(2 * 1024 * 1024))
  }
}
