import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  increment,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  type Unsubscribe
} from "firebase/firestore";
import { deleteObject, getDownloadURL, ref, uploadBytesResumable } from "firebase/storage";
import { db, storage } from "./firebase";
import type {
  AdminRecord,
  CallSession,
  CallSignal,
  ChatMessage,
  ChatThread,
  CreatorProfile,
  PublishedVideo,
  UserAccount,
  VideoComment
} from "./types";

function requireDb() {
  if (!db) throw new Error("Firebase Firestore is not configured.");
  return db;
}

export function toTimestampMillis(ts?: { toMillis?: () => number } | null): number {
  return ts && typeof ts.toMillis === "function" ? ts.toMillis() : 0;
}

export function resolveIsUserOnline(
  isOnlineFlag?: boolean | null,
  lastActiveAt?: { toMillis?: () => number } | null,
  updatedAt?: { toMillis?: () => number } | null,
  nowMs: number = Date.now(),
  windowMs: number = 5 * 60 * 1000
): boolean {
  if (!isOnlineFlag) return false;
  const refMs = toTimestampMillis(lastActiveAt) || toTimestampMillis(updatedAt);
  if (!refMs) return true;
  return nowMs - refMs <= windowMs;
}

export async function ensureUserAndCreatorInitialized(
  uid: string,
  displayName: string,
  email: string,
  photoUrl?: string | null
): Promise<void> {
  const firestore = requireDb();
  const safeName = (displayName || "").trim().slice(0, 60) || "PulseCast Creator";
  const safeEmail = (email || "").trim().slice(0, 254) || `${uid}@pulsecast.app`;
  const cleanPhotoUrl = photoUrl?.trim().slice(0, 2000) || null;

  const userRef = doc(firestore, "users", uid);
  const userSnap = await getDoc(userRef);
  if (!userSnap.exists()) {
    await setDoc(userRef, {
      userId: uid,
      email: safeEmail,
      displayName: safeName,
      pushCallAlerts: true,
      pushMessageAlerts: true,
      hdVideoDefault: true,
      isOnline: true,
      ...(cleanPhotoUrl ? { photoUrl: cleanPhotoUrl } : {}),
      lastActiveAt: serverTimestamp(),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  } else {
    await updateDoc(userRef, {
      isOnline: true,
      ...(cleanPhotoUrl ? { photoUrl: cleanPhotoUrl } : {}),
      lastActiveAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  }

  const creatorRef = doc(firestore, "creators", uid);
  const creatorSnap = await getDoc(creatorRef);
  if (!creatorSnap.exists()) {
    const baseHandle = safeName
      .toLowerCase()
      .replace(/[^a-z0-9_]/g, "")
      .slice(0, 20) || "creator";
    const suffix = uid.slice(0, 4).toLowerCase();
    await setDoc(creatorRef, {
      userId: uid,
      displayName: safeName,
      handle: `${baseHandle}_${suffix}`,
      bio: "Audio, video & live studio creator on PulseCast",
      status: "online",
      isDiscoverable: true,
      isOnline: true,
      ...(cleanPhotoUrl ? { photoUrl: cleanPhotoUrl } : {}),
      lastActiveAt: serverTimestamp(),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  } else {
    await updateDoc(creatorRef, {
      isOnline: true,
      ...(cleanPhotoUrl ? { photoUrl: cleanPhotoUrl } : {}),
      lastActiveAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  }
}

export async function updateUserActivity(uid: string, isOnline: boolean): Promise<void> {
  if (!uid || !db) return;
  const userRef = doc(db, "users", uid);
  const creatorRef = doc(db, "creators", uid);
  const updates = {
    isOnline,
    lastActiveAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };
  const [userSnap, creatorSnap] = await Promise.all([getDoc(userRef), getDoc(creatorRef)]);
  const batch = writeBatch(db);
  let hasUpdates = false;
  if (userSnap.exists()) {
    batch.update(userRef, updates);
    hasUpdates = true;
  }
  if (creatorSnap.exists()) {
    batch.update(creatorRef, updates);
    hasUpdates = true;
  }
  if (hasUpdates) {
    await batch.commit();
  }
}

export async function updateProfilePhoto(
  uid: string,
  avatarBase64: string | null,
  photoUrl?: string | null
): Promise<void> {
  const firestore = requireDb();
  const cleanBase64 = avatarBase64 && avatarBase64.trim().length > 0 && avatarBase64.length <= 250000
    ? avatarBase64
    : null;
  const cleanPhotoUrl = photoUrl?.trim().slice(0, 2000) || null;
  const updates: Record<string, unknown> = {
    avatarBase64: cleanBase64,
    updatedAt: serverTimestamp()
  };
  if (cleanPhotoUrl) {
    updates.photoUrl = cleanPhotoUrl;
  }
  const userRef = doc(firestore, "users", uid);
  const creatorRef = doc(firestore, "creators", uid);
  const [userSnap, creatorSnap] = await Promise.all([getDoc(userRef), getDoc(creatorRef)]);
  const batch = writeBatch(firestore);
  let hasUpdates = false;
  if (creatorSnap.exists()) {
    batch.update(creatorRef, updates);
    hasUpdates = true;
  }
  if (userSnap.exists()) {
    batch.update(userRef, updates);
    hasUpdates = true;
  }
  if (hasUpdates) {
    await batch.commit();
  }
}

export function observeUserAccount(
  uid: string,
  onData: (account: UserAccount | null) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  return onSnapshot(
    doc(firestore, "users", uid),
    (snap) => {
      onData(snap.exists() ? ({ id: snap.id, ...snap.data() } as UserAccount) : null);
    },
    (err) => onError?.(err)
  );
}

export function observeCreators(
  onData: (creators: CreatorProfile[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  const q = query(collection(firestore, "creators"), where("isDiscoverable", "==", true));
  return onSnapshot(
    q,
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as CreatorProfile)
        .sort((a, b) => a.displayName.toLowerCase().localeCompare(b.displayName.toLowerCase()));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export async function updateCreatorProfile(
  uid: string,
  displayName: string,
  handle: string,
  bio: string,
  status: string
): Promise<void> {
  const firestore = requireDb();
  const cleanName = displayName.trim().slice(0, 60) || "Creator";
  const cleanHandle = handle.trim().toLowerCase().replace(/[^a-z0-9_]/g, "").slice(0, 32) || "creator";
  const cleanBio = bio.trim().slice(0, 240);
  await updateDoc(doc(firestore, "creators", uid), {
    displayName: cleanName,
    handle: cleanHandle,
    bio: cleanBio,
    status,
    updatedAt: serverTimestamp()
  });
}

export async function updateUserPreferences(
  uid: string,
  pushCallAlerts: boolean,
  pushMessageAlerts: boolean,
  hdVideoDefault: boolean
): Promise<void> {
  const firestore = requireDb();
  await updateDoc(doc(firestore, "users", uid), {
    pushCallAlerts,
    pushMessageAlerts,
    hdVideoDefault,
    updatedAt: serverTimestamp()
  });
}

// --- Video Publishing & Storage ---

export function observePublicVideos(
  onData: (videos: PublishedVideo[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  const q = query(collection(firestore, "videos"), where("visibility", "==", "public"));
  return onSnapshot(
    q,
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as PublishedVideo)
        .sort((a, b) => toTimestampMillis(b.createdAt) - toTimestampMillis(a.createdAt));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export function observeMyVideos(
  uid: string,
  onData: (videos: PublishedVideo[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  const q = query(collection(firestore, "videos"), where("authorId", "==", uid));
  return onSnapshot(
    q,
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as PublishedVideo)
        .sort((a, b) => toTimestampMillis(b.createdAt) - toTimestampMillis(a.createdAt));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export async function uploadAndPublishVideo(params: {
  uid: string;
  authorName: string;
  title: string;
  description?: string;
  category: string;
  videoFile?: File | null;
  externalVideoUrl?: string;
  coverKey?: string;
  thumbnailBase64?: string | null;
  visibility: "public" | "private";
  durationSec: number;
  onProgress?: (stage: "uploading" | "saving", progress: number) => void;
}): Promise<string> {
  const firestore = requireDb();
  const docRef = doc(collection(firestore, "videos"));
  let uploadedStorageRefPath: string | null = null;
  let resolvedVideoUri: string | undefined = params.externalVideoUrl?.trim() || undefined;

  if (params.videoFile) {
    if (!storage) throw new Error("Firebase Storage is not configured.");
    const safeMime = params.videoFile.type.startsWith("video/") ? params.videoFile.type : "video/mp4";
    const ext = safeMime.includes("webm") ? "webm" : safeMime.includes("quicktime") ? "mov" : "mp4";
    const objectPath = `videos/${params.uid}/${docRef.id}.${ext}`;
    const storageRef = ref(storage, objectPath);
    uploadedStorageRefPath = objectPath;

    params.onProgress?.("uploading", 0);
    resolvedVideoUri = await new Promise<string>((resolve, reject) => {
      const task = uploadBytesResumable(storageRef, params.videoFile!, {
        contentType: safeMime,
        customMetadata: {
          authorId: params.uid,
          videoId: docRef.id
        }
      });
      task.on(
        "state_changed",
        (snapshot) => {
          if (snapshot.totalBytes > 0) {
            params.onProgress?.("uploading", snapshot.bytesTransferred / snapshot.totalBytes);
          }
        },
        (err) => reject(err),
        async () => {
          try {
            const url = await getDownloadURL(task.snapshot.ref);
            params.onProgress?.("uploading", 1);
            resolve(url);
          } catch (err) {
            reject(err);
          }
        }
      );
    });
  }

  params.onProgress?.("saving", 1);
  const payload: Record<string, unknown> = {
    authorId: params.uid,
    authorName: params.authorName.trim().slice(0, 60) || "Creator",
    title: params.title.trim().slice(0, 120),
    category: params.category,
    visibility: params.visibility,
    likesCount: 0,
    commentsCount: 0,
    durationSec: Math.max(0, Math.min(36000, Math.round(params.durationSec))),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };

  if (params.description?.trim()) payload.description = params.description.trim().slice(0, 1000);
  if (resolvedVideoUri) payload.videoUri = resolvedVideoUri.slice(0, 2000);
  if (params.coverKey?.trim()) payload.coverKey = params.coverKey.trim().slice(0, 64);
  if (params.thumbnailBase64 && params.thumbnailBase64.length <= 250000) {
    payload.thumbnailBase64 = params.thumbnailBase64;
  }

  try {
    await setDoc(docRef, payload);
    return docRef.id;
  } catch (err) {
    if (uploadedStorageRefPath && storage) {
      await deleteObject(ref(storage, uploadedStorageRefPath)).catch(() => {});
    }
    throw err;
  }
}

export async function deleteVideo(videoId: string, videoUri?: string | null): Promise<void> {
  const firestore = requireDb();
  await deleteDoc(doc(firestore, "videos", videoId));
  if (
    storage &&
    videoUri &&
    (videoUri.startsWith("https://firebasestorage.googleapis.com/") || videoUri.startsWith("gs://"))
  ) {
    await deleteObject(ref(storage, videoUri)).catch(() => {});
  }
}

export function observeIsVideoLiked(
  videoId: string,
  uid: string,
  onData: (liked: boolean) => void
): Unsubscribe {
  const firestore = requireDb();
  return onSnapshot(doc(firestore, "videos", videoId, "likes", uid), (snap) => {
    onData(snap.exists());
  });
}

export async function toggleVideoLike(videoId: string, uid: string): Promise<void> {
  const firestore = requireDb();
  const videoRef = doc(firestore, "videos", videoId);
  const likeRef = doc(firestore, "videos", videoId, "likes", uid);
  const snap = await getDoc(likeRef);
  const batch = writeBatch(firestore);
  if (snap.exists()) {
    batch.delete(likeRef);
    batch.update(videoRef, { likesCount: increment(-1), updatedAt: serverTimestamp() });
  } else {
    batch.set(likeRef, { userId: uid, createdAt: serverTimestamp() });
    batch.update(videoRef, { likesCount: increment(1), updatedAt: serverTimestamp() });
  }
  await batch.commit();
}

export function observeComments(
  videoId: string,
  onData: (comments: VideoComment[]) => void
): Unsubscribe {
  const firestore = requireDb();
  const q = query(
    collection(firestore, "videos", videoId, "comments"),
    where("videoId", "==", videoId)
  );
  return onSnapshot(q, (snap) => {
    const list = snap.docs
      .map((d) => ({ id: d.id, ...d.data() }) as VideoComment)
      .sort((a, b) => toTimestampMillis(b.createdAt) - toTimestampMillis(a.createdAt));
    onData(list);
  });
}

export async function addComment(
  videoId: string,
  uid: string,
  authorName: string,
  text: string
): Promise<void> {
  const firestore = requireDb();
  const videoRef = doc(firestore, "videos", videoId);
  const commentRef = doc(collection(firestore, "videos", videoId, "comments"));
  const batch = writeBatch(firestore);
  batch.set(commentRef, {
    videoId,
    authorId: uid,
    authorName: authorName.trim().slice(0, 60) || "Creator",
    text: text.trim().slice(0, 500),
    createdAt: serverTimestamp()
  });
  batch.update(videoRef, {
    commentsCount: increment(1),
    updatedAt: serverTimestamp()
  });
  await batch.commit();
}

// --- Real-Time Messaging ---

export function observeChats(
  uid: string,
  onData: (chats: ChatThread[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  const q = query(collection(firestore, "chats"), where("participantIds", "array-contains", uid));
  return onSnapshot(
    q,
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as ChatThread)
        .sort(
          (a, b) =>
            (toTimestampMillis(b.lastMessageAt) || toTimestampMillis(b.createdAt)) -
            (toTimestampMillis(a.lastMessageAt) || toTimestampMillis(a.createdAt))
        );
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export async function createChatThread(params: {
  uid: string;
  creatorName: string;
  title: string;
  isGroup: boolean;
  participantIds: string[];
  participantNames: string[];
}): Promise<string> {
  const firestore = requireDb();
  const chatRef = doc(collection(firestore, "chats"));
  const distinctIds = Array.from(new Set([params.uid, ...params.participantIds])).slice(0, 20);
  const distinctNames = Array.from(new Set([params.creatorName, ...params.participantNames])).slice(0, 20);
  await setDoc(chatRef, {
    title: params.title.trim().slice(0, 80) || "Studio Thread",
    isGroup: params.isGroup,
    participantIds: distinctIds,
    participantNames: distinctNames,
    createdBy: params.uid,
    lastMessageText: "Thread started",
    lastMessageSenderId: params.uid,
    lastMessageAt: serverTimestamp(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  return chatRef.id;
}

export function observeMessages(
  chatId: string,
  uid: string,
  onData: (messages: ChatMessage[]) => void
): Unsubscribe {
  const firestore = requireDb();
  const q = query(
    collection(firestore, "chats", chatId, "messages"),
    where("participantIds", "array-contains", uid)
  );
  return onSnapshot(q, (snap) => {
    const list = snap.docs
      .map((d) => ({ id: d.id, ...d.data() }) as ChatMessage)
      .sort((a, b) => toTimestampMillis(a.createdAt) - toTimestampMillis(b.createdAt));
    onData(list);
  });
}

export async function sendMessage(params: {
  chatId: string;
  uid: string;
  senderName: string;
  participantIds: string[];
  messageType?: "text" | "call_event";
  text: string;
}): Promise<void> {
  const firestore = requireDb();
  const chatRef = doc(firestore, "chats", params.chatId);
  const msgRef = doc(collection(firestore, "chats", params.chatId, "messages"));
  const participants = Array.from(new Set([...params.participantIds, params.uid])).slice(0, 20);
  const safeText = params.text.trim().slice(0, 2000);
  const batch = writeBatch(firestore);
  batch.set(msgRef, {
    chatId: params.chatId,
    senderId: params.uid,
    senderName: params.senderName.trim().slice(0, 60) || "Creator",
    messageType: params.messageType || "text",
    text: safeText,
    participantIds: participants,
    isRead: false,
    createdAt: serverTimestamp()
  });
  batch.update(chatRef, {
    lastMessageText: safeText.slice(0, 500),
    lastMessageSenderId: params.uid,
    lastMessageAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  await batch.commit();
}

// --- Audio & Video Calling ---

export function observeCallSessions(
  uid: string,
  onData: (sessions: CallSession[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  const q = query(
    collection(firestore, "call_sessions"),
    where("participantIds", "array-contains", uid)
  );
  return onSnapshot(
    q,
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as CallSession)
        .sort((a, b) => toTimestampMillis(b.createdAt) - toTimestampMillis(a.createdAt));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export async function createCallSession(
  uid: string,
  hostName: string,
  callType: "audio" | "video" | "group",
  invitedParticipantIds: string[] = [],
  invitedParticipantNames: string[] = []
): Promise<CallSession> {
  const firestore = requireDb();
  const suffix = Math.random().toString(36).substring(2, 6).toUpperCase();
  const code = `PULSE-${suffix}`;
  const cleanName = hostName.trim().slice(0, 60) || "Creator";
  const mergedIds = Array.from(
    new Set([uid, ...invitedParticipantIds.map((id) => id.trim()).filter(Boolean)])
  ).slice(0, 12);
  const mergedNames = Array.from(
    new Set([cleanName, ...invitedParticipantNames.map((n) => n.trim()).filter(Boolean)])
  ).slice(0, 12);
  await setDoc(doc(firestore, "call_sessions", code), {
    hostId: uid,
    hostName: cleanName,
    roomCode: code,
    callType,
    status: "ringing",
    participantIds: mergedIds,
    participantNames: mergedNames,
    durationSec: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  return {
    id: code,
    hostId: uid,
    hostName: cleanName,
    roomCode: code,
    callType,
    status: "ringing",
    participantIds: mergedIds,
    participantNames: mergedNames,
    durationSec: 0
  };
}

export async function joinCallSession(
  roomCode: string,
  uid: string,
  userName: string
): Promise<CallSession> {
  const firestore = requireDb();
  const normalized = roomCode.trim().toUpperCase().replace(/[^A-Z0-9-]/g, "");
  if (normalized.length < 4 || normalized.length > 32) {
    throw new Error("Room code must be 4-32 characters.");
  }
  const callRef = doc(firestore, "call_sessions", normalized);
  const snap = await getDoc(callRef);
  if (!snap.exists()) {
    throw new Error(`No active call room found for ${normalized}`);
  }
  const session = { id: snap.id, ...snap.data() } as CallSession;
  if (session.status === "ended") {
    throw new Error("This call session has already ended.");
  }
  const updatedIds = Array.from(new Set([...session.participantIds, uid])).slice(0, 12);
  const updatedNames = Array.from(
    new Set([...(session.participantNames || []), userName.trim() || "Guest"])
  ).slice(0, 12);
  await updateDoc(callRef, {
    participantIds: updatedIds,
    participantNames: updatedNames,
    status: "active",
    updatedAt: serverTimestamp()
  });
  return { ...session, participantIds: updatedIds, participantNames: updatedNames, status: "active" };
}

export async function endCallSession(callId: string, durationSec: number): Promise<void> {
  const firestore = requireDb();
  await updateDoc(doc(firestore, "call_sessions", callId), {
    status: "ended",
    durationSec: Math.max(0, Math.min(86400, Math.round(durationSec))),
    updatedAt: serverTimestamp()
  });
}

export async function declineCallSession(callId: string): Promise<void> {
  await endCallSession(callId, 0);
}

export function observeCallSignals(
  callId: string,
  recipientUid: string,
  onData: (signals: CallSignal[]) => void
): Unsubscribe {
  const firestore = requireDb();
  const q = query(
    collection(firestore, "call_sessions", callId, "signals"),
    where("recipientId", "==", recipientUid)
  );
  return onSnapshot(q, (snap) => {
    const list = snap.docs
      .map((d) => ({ id: d.id, ...d.data() }) as CallSignal)
      .sort((a, b) => toTimestampMillis(a.createdAt) - toTimestampMillis(b.createdAt));
    onData(list);
  });
}

export async function sendCallSignal(params: {
  callId: string;
  senderId: string;
  recipientId: string;
  type: "offer" | "answer" | "candidate";
  sdp?: string | null;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
  candidate?: string | null;
}): Promise<void> {
  const firestore = requireDb();
  const normalizedCallId = params.callId.trim().toUpperCase().replace(/[^A-Z0-9-]/g, "");
  if (normalizedCallId.length < 4 || normalizedCallId.length > 32) {
    throw new Error("Invalid callId for signaling.");
  }
  const signalRef = doc(collection(firestore, "call_sessions", normalizedCallId, "signals"));
  const payload: Record<string, unknown> = {
    callId: normalizedCallId,
    senderId: params.senderId,
    recipientId: params.recipientId,
    type: params.type,
    createdAt: serverTimestamp()
  };
  if (params.sdp != null) payload.sdp = params.sdp.slice(0, 32768);
  if (params.sdpMid != null) payload.sdpMid = params.sdpMid.slice(0, 64);
  if (params.sdpMLineIndex != null) {
    payload.sdpMLineIndex = Math.max(0, Math.min(32, Math.round(params.sdpMLineIndex)));
  }
  if (params.candidate != null) payload.candidate = params.candidate.slice(0, 2048);
  await setDoc(signalRef, payload);
}

// --- Admin Panel & RBAC Moderation ---

export function observeIsAdminDoc(
  uid: string,
  onData: (isAdmin: boolean) => void
): Unsubscribe {
  const firestore = requireDb();
  return onSnapshot(
    doc(firestore, "admins", uid),
    (snap) => onData(snap.exists()),
    () => onData(false)
  );
}

export function observeAdmins(
  onData: (admins: AdminRecord[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  return onSnapshot(
    collection(firestore, "admins"),
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as AdminRecord)
        .sort((a, b) => a.displayName.toLowerCase().localeCompare(b.displayName.toLowerCase()));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export function observeAllUsersForAdmin(
  onData: (users: UserAccount[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  return onSnapshot(
    collection(firestore, "users"),
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as UserAccount)
        .sort((a, b) => a.displayName.toLowerCase().localeCompare(b.displayName.toLowerCase()));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export function observeAllCreatorsForAdmin(
  onData: (creators: CreatorProfile[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  return onSnapshot(
    collection(firestore, "creators"),
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as CreatorProfile)
        .sort((a, b) => a.displayName.toLowerCase().localeCompare(b.displayName.toLowerCase()));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export function observeAllVideosForAdmin(
  onData: (videos: PublishedVideo[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  return onSnapshot(
    collection(firestore, "videos"),
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as PublishedVideo)
        .sort((a, b) => toTimestampMillis(b.createdAt) - toTimestampMillis(a.createdAt));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export function observeAllCallSessionsForAdmin(
  onData: (sessions: CallSession[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = requireDb();
  return onSnapshot(
    collection(firestore, "call_sessions"),
    (snap) => {
      const list = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }) as CallSession)
        .sort((a, b) => toTimestampMillis(b.createdAt) - toTimestampMillis(a.createdAt));
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export async function adminUpdateVideoVisibility(
  videoId: string,
  visibility: "public" | "private"
): Promise<void> {
  const firestore = requireDb();
  await updateDoc(doc(firestore, "videos", videoId), {
    visibility,
    updatedAt: serverTimestamp()
  });
}

export async function adminUpdateCreatorModeration(
  creatorId: string,
  isDiscoverable: boolean,
  status: string
): Promise<void> {
  const firestore = requireDb();
  await updateDoc(doc(firestore, "creators", creatorId), {
    isDiscoverable,
    status,
    updatedAt: serverTimestamp()
  });
}

export async function adminDeleteCallSession(callId: string): Promise<void> {
  const firestore = requireDb();
  await deleteDoc(doc(firestore, "call_sessions", callId));
}

export async function adminGrantRole(params: {
  targetUid: string;
  displayName: string;
  email: string;
  role: "super_admin" | "moderator";
  grantedByUid: string;
}): Promise<void> {
  const firestore = requireDb();
  const cleanUid = params.targetUid.trim();
  if (!cleanUid) throw new Error("Valid User UID is required.");
  await setDoc(doc(firestore, "admins", cleanUid), {
    userId: cleanUid,
    displayName: params.displayName.trim().slice(0, 60) || "Studio Moderator",
    email: params.email.trim().slice(0, 254),
    role: params.role,
    grantedBy: params.grantedByUid,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
}

export async function adminRevokeRole(targetUid: string): Promise<void> {
  const firestore = requireDb();
  await deleteDoc(doc(firestore, "admins", targetUid));
}

