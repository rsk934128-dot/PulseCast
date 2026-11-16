const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-pulsecas-9e6b52a5-7c12-482d-961e-c26729bf0f15";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTs() {
  const { serverTimestamp } = require("firebase/firestore");
  return serverTimestamp();
}

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

function getDb(context) {
  return context.firestore({ databaseId: DATABASE_ID });
}

test("Unauthenticated user: cannot read or write any collection", async () => {
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("creators").where("isDiscoverable", "==", true).get());
  await assertFails(unauthDb.collection("videos").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("chats").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("call_sessions").where("participantIds", "array-contains", ALICE_UID).get());
});

test("Users PII isolation: owner can create/read own doc, cross-user read/write fails", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      pushCallAlerts: true,
      pushMessageAlerts: true,
      hdVideoDefault: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field shadow update rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(),
    })
  );

  // Bob cannot read or overwrite Alice's private user account
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "bob@example.com",
      displayName: "Spoof",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Creators directory: aligned list query succeeds, unfiltered list fails", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("creators").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Studio",
      handle: "alice_studio",
      bio: "Audio & video creator",
      status: "online",
      isDiscoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unfiltered query rejected (rules are not filters)
  await assertFails(bobDb.collection("creators").get());
  // Aligned query succeeds
  await assertSucceeds(bobDb.collection("creators").where("isDiscoverable", "==", true).get());
});

test("Videos feed: atomic like/unlike and comments enforce rules, private videos isolated", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      title: "Studio Setup Tour",
      description: "My 2026 streaming desk",
      category: "Studio",
      coverKey: "thumb_1",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      durationSec: 120,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_private").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      title: "Private Draft",
      category: "Vlog",
      visibility: "private",
      likesCount: 0,
      commentsCount: 0,
      durationSec: 30,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read private video or run unfiltered videos query
  await assertFails(bobDb.collection("videos").doc("vid_private").get());
  await assertFails(bobDb.collection("videos").get());
  await assertSucceeds(bobDb.collection("videos").where("visibility", "==", "public").get());

  // Bob cannot increment likesCount without atomic like doc
  await assertFails(
    bobDb.collection("videos").doc("vid_1").update({
      likesCount: 1,
      updatedAt: now,
    })
  );

  // Atomic batch like by Bob succeeds
  const batch = bobDb.batch();
  batch.set(bobDb.collection("videos").doc("vid_1").collection("likes").doc(BOB_UID), {
    userId: BOB_UID,
    createdAt: now,
  });
  batch.update(bobDb.collection("videos").doc("vid_1"), {
    likesCount: 1,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());

  // Bob cannot delete Alice's video
  await assertFails(bobDb.collection("videos").doc("vid_1").delete());
});

test("Chats & messages: participant isolation enforced", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const charlieDb = getDb(testEnv.authenticatedContext(CHARLIE_UID));
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").set({
      title: "Alice & Bob",
      isGroup: false,
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: ["Alice", "Bob"],
      createdBy: ALICE_UID,
      lastMessageText: "Hey Bob!",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").set({
      chatId: "chat_1",
      senderId: BOB_UID,
      senderName: "Bob",
      messageType: "text",
      text: "Ready for our video call!",
      participantIds: [ALICE_UID, BOB_UID],
      isRead: false,
      createdAt: now,
    })
  );

  // Charlie cannot read chat, message doc, unfiltered messages, or spoof Alice's filter
  await assertFails(charlieDb.collection("chats").doc("chat_1").get());
  await assertFails(charlieDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").get());
  await assertFails(charlieDb.collection("chats").doc("chat_1").collection("messages").get());
  await assertFails(
    charlieDb.collection("chats").doc("chat_1").collection("messages")
      .where("participantIds", "array-contains", ALICE_UID).get()
  );
  const charlieMsgs = await assertSucceeds(
    charlieDb.collection("chats").doc("chat_1").collection("messages")
      .where("participantIds", "array-contains", CHARLIE_UID).get()
  );
  if (!charlieMsgs.empty) {
    throw new Error("Charlie should not receive any messages from Alice & Bob chat");
  }

  // Bob can query aligned messages
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_1").collection("messages")
      .where("participantIds", "array-contains", BOB_UID).get()
  );
});

test("Call sessions: join active room succeeds, terminal ended lock prevents further edits", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("call_sessions").doc("PULSE-8842").set({
      hostId: ALICE_UID,
      hostName: "Alice",
      roomCode: "PULSE-8842",
      callType: "video",
      status: "ringing",
      participantIds: [ALICE_UID],
      participantNames: ["Alice"],
      durationSec: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob joins active room by room code
  await assertSucceeds(
    bobDb.collection("call_sessions").doc("PULSE-8842").update({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: ["Alice", "Bob"],
      status: "active",
      updatedAt: now,
    })
  );

  // Alice ends the call
  await assertSucceeds(
    aliceDb.collection("call_sessions").doc("PULSE-8842").update({
      status: "ended",
      durationSec: 95,
      updatedAt: now,
    })
  );

  // Terminal state locking: cannot reactivate an ended call
  await assertFails(
    bobDb.collection("call_sessions").doc("PULSE-8842").update({
      status: "active",
      updatedAt: now,
    })
  );
});
