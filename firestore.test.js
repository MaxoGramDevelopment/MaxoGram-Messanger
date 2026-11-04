const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const EVE_UID = "eve_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// 1. Unauthenticated checks
test("Unauthenticated: cannot read or write users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    username: "alice",
    displayName: "Alice",
    createdAt: new Date(),
  }));
});

// 2. User profile create & isolation
test("Alice can create her own profile, cannot create Bob's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Alice creates her own
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    username: "alice",
    displayName: "Alice Wonderland",
    createdAt: new Date(),
  }));

  // Alice cannot spoof Bob's profile
  await assertFails(aliceDb.collection("users").doc(BOB_UID).set({
    userId: BOB_UID,
    username: "bob",
    displayName: "Bob",
    createdAt: new Date(),
  }));
});

test("Signed-in user can read other users for search", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob",
      displayName: "Bob Builder",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(BOB_UID).get());
});

// 3. Chat and messaging access control
test("Alice and Bob chat access and Eve isolation", async () => {
  const chatId = "chat_alice_bob";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  // Alice creates chat with Bob
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).set({
    chatId: chatId,
    memberIds: [ALICE_UID, BOB_UID],
    createdAt: new Date(),
  }));

  // Alice can read chat
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).get());

  // Eve cannot read Alice-Bob chat
  await assertFails(eveDb.collection("chats").doc(chatId).get());

  // Alice can send message
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg1").set({
    messageId: "msg1",
    chatId: chatId,
    senderId: ALICE_UID,
    senderName: "Alice",
    text: "Hello Bob!",
    memberIds: [ALICE_UID, BOB_UID],
    createdAt: new Date(),
  }));

  // Eve cannot read or send messages in Alice-Bob chat
  await assertFails(eveDb.collection("chats").doc(chatId).collection("messages").doc("msg1").get());
  await assertFails(eveDb.collection("chats").doc(chatId).collection("messages").doc("msg2").set({
    messageId: "msg2",
    chatId: chatId,
    senderId: EVE_UID,
    senderName: "Eve",
    text: "Hacking...",
    memberIds: [ALICE_UID, BOB_UID],
    createdAt: new Date(),
  }));
});
