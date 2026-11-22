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
const SUPER_ADMIN_UID = "admin_uid_001";
const ADMIN_EMAIL = "rsk755734@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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
  await assertFails(unauthDb.collection("admins").doc(ALICE_UID).get());
});

test("Users PII isolation: owner can create/read own doc, cross-user read/write fails", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      pushCallAlerts: true,
      pushMessageAlerts: true,
      hdVideoDefault: true,
      isOnline: true,
      lastActiveAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner can update isOnline and lastActiveAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isOnline: false,
      lastActiveAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Tampered future lastActiveAt rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isOnline: true,
      lastActiveAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Ghost field shadow update rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
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
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("creators").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Studio",
      handle: "alice_studio",
      bio: "Audio & video creator",
      status: "online",
      isDiscoverable: true,
      isOnline: true,
      lastActiveAt: now,
      photoUrl: "https://lh3.googleusercontent.com/a/alice-avatar",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner can update presence & profile photo fields; Bob cannot spoof Alice's online state or photo
  await assertSucceeds(
    aliceDb.collection("creators").doc(ALICE_UID).update({
      isOnline: false,
      avatarBase64: "dGVzdF9hdmF0YXJfYmFzZTY0",
      lastActiveAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("creators").doc(ALICE_UID).update({
      isOnline: true,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    bobDb.collection("creators").doc(ALICE_UID).update({
      photoUrl: "https://evil.example.com/spoof.png",
      updatedAt: new Date(Date.now() - 300),
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
  const now = new Date(Date.now() - 1000);

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

  // Alice uploads chunked media to public video and updates videoUri; Bob can read public chunks but cannot spoof/delete
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").collection("media_chunks").doc("chunk_0").set({
      videoId: "vid_1",
      authorId: ALICE_UID,
      index: 0,
      totalChunks: 1,
      mimeType: "video/mp4",
      dataBase64: "AAAAIGZ0eXBpc29t",
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").update({
      videoUri: "firestore://videos/vid_1?ext=mp4",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    bobDb.collection("videos").doc("vid_1").collection("media_chunks").where("videoId", "==", "vid_1").get()
  );
  await assertFails(
    bobDb.collection("videos").doc("vid_1").collection("media_chunks").doc("chunk_1").set({
      videoId: "vid_1",
      authorId: BOB_UID,
      index: 1,
      totalChunks: 2,
      mimeType: "video/mp4",
      dataBase64: "AAAA",
      createdAt: now,
    })
  );
  await assertFails(
    bobDb.collection("videos").doc("vid_1").collection("media_chunks").doc("chunk_0").delete()
  );

  // Private video chunks are isolated from Bob
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_private").collection("media_chunks").doc("chunk_0").set({
      videoId: "vid_private",
      authorId: ALICE_UID,
      index: 0,
      totalChunks: 1,
      mimeType: "video/mp4",
      dataBase64: "AAAA",
      createdAt: now,
    })
  );
  await assertFails(
    bobDb.collection("videos").doc("vid_private").collection("media_chunks").where("videoId", "==", "vid_private").get()
  );

  // Bob cannot delete Alice's video
  await assertFails(bobDb.collection("videos").doc("vid_1").delete());
});

test("Chats & messages: participant isolation enforced", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const charlieDb = getDb(testEnv.authenticatedContext(CHARLIE_UID));
  const now = new Date(Date.now() - 1000);

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
  const now = new Date(Date.now() - 1000);

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

  // Alice and Bob can exchange WebRTC signaling offers/answers while call is active; Charlie is blocked
  await assertSucceeds(
    aliceDb.collection("call_sessions").doc("PULSE-8842").collection("signals").doc("sig_offer_1").set({
      callId: "PULSE-8842",
      senderId: ALICE_UID,
      recipientId: BOB_UID,
      type: "offer",
      sdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      createdAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("call_sessions").doc("PULSE-8842").collection("signals")
      .where("recipientId", "==", BOB_UID).get()
  );
  const charlieDb = getDb(testEnv.authenticatedContext(CHARLIE_UID));
  await assertFails(
    charlieDb.collection("call_sessions").doc("PULSE-8842").collection("signals").doc("sig_offer_1").get()
  );
  await assertFails(
    charlieDb.collection("call_sessions").doc("PULSE-8842").collection("signals").doc("sig_spoof").set({
      callId: "PULSE-8842",
      senderId: CHARLIE_UID,
      recipientId: BOB_UID,
      type: "offer",
      sdp: "v=0",
      createdAt: now,
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

  // Terminal state locking: cannot reactivate an ended call or send new signals
  await assertFails(
    bobDb.collection("call_sessions").doc("PULSE-8842").update({
      status: "active",
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("call_sessions").doc("PULSE-8842").collection("signals").doc("sig_after_end").set({
      callId: "PULSE-8842",
      senderId: BOB_UID,
      recipientId: ALICE_UID,
      type: "candidate",
      candidate: "candidate:1 1 udp 2122260223 192.0.2.1 54400 typ host",
      sdpMid: "0",
      sdpMLineIndex: 0,
      createdAt: now,
    })
  );
});

test("Admin RBAC: email spoofing fails, verified admin can moderate & manage admins", async () => {
  const now = new Date(Date.now() - 2000);
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const spoofedAdminDb = getDb(
    testEnv.authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
  );
  const adminDb = getDb(
    testEnv.authenticatedContext(SUPER_ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
  );
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));

  // Alice creates her account, creator profile, private video, and active call
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
  await assertSucceeds(
    aliceDb.collection("creators").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_creator",
      isDiscoverable: true,
      status: "online",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("videos").doc("alice_priv").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      title: "Private Review",
      category: "Studio",
      visibility: "private",
      likesCount: 0,
      commentsCount: 0,
      durationSec: 45,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Normal user can probe own admin doc without permission error, but cannot list admins or self-promote
  await assertSucceeds(aliceDb.collection("admins").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("admins").get());
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "moderator",
      grantedBy: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Email spoofing (email_verified: false) MUST be rejected from admin reads/writes
  await assertFails(spoofedAdminDb.collection("admins").get());
  await assertFails(spoofedAdminDb.collection("videos").get());
  await assertFails(spoofedAdminDb.collection("users").get());

  // Verified bootstrapped admin can list all users, creators, and private videos
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(adminDb.collection("creators").get());
  await assertSucceeds(adminDb.collection("videos").get());
  await assertSucceeds(adminDb.collection("call_sessions").get());

  // Admin can moderate creator discoverability and video visibility
  await assertSucceeds(
    adminDb.collection("creators").doc(ALICE_UID).update({
      isDiscoverable: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    adminDb.collection("videos").doc("alice_priv").update({
      visibility: "public",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin grants moderator role to Bob
  await assertSucceeds(
    adminDb.collection("admins").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob Mod",
      email: "bob@example.com",
      role: "moderator",
      grantedBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob (now in /admins/{BOB_UID}) can list videos & delete moderated video
  await assertSucceeds(bobDb.collection("videos").get());
  await assertSucceeds(bobDb.collection("videos").doc("alice_priv").delete());

  // Admin can revoke Bob's role
  await assertSucceeds(adminDb.collection("admins").doc(BOB_UID).delete());
  await assertFails(bobDb.collection("videos").get());
});

