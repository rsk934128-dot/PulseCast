import type { Timestamp } from "firebase/firestore";

export interface UserAccount {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  pushCallAlerts: boolean;
  pushMessageAlerts: boolean;
  hdVideoDefault: boolean;
  createdAt?: Timestamp | null;
  updatedAt?: Timestamp | null;
}

export interface CreatorProfile {
  id: string;
  userId: string;
  displayName: string;
  handle: string;
  bio?: string | null;
  status?: "online" | "in_call" | "studio" | "away" | string | null;
  isDiscoverable: boolean;
  createdAt?: Timestamp | null;
  updatedAt?: Timestamp | null;
}

export type VideoCategory = "Tech" | "Studio" | "Podcast" | "Music" | "Vlog" | "Live";

export interface PublishedVideo {
  id: string;
  authorId: string;
  authorName: string;
  title: string;
  description?: string | null;
  category: VideoCategory | string;
  videoUri?: string | null;
  coverKey?: string | null;
  thumbnailBase64?: string | null;
  visibility: "public" | "private" | string;
  likesCount: number;
  commentsCount: number;
  durationSec: number;
  createdAt?: Timestamp | null;
  updatedAt?: Timestamp | null;
}

export interface VideoComment {
  id: string;
  videoId: string;
  authorId: string;
  authorName: string;
  text: string;
  createdAt?: Timestamp | null;
}

export interface ChatThread {
  id: string;
  title: string;
  isGroup: boolean;
  participantIds: string[];
  participantNames?: string[] | null;
  createdBy: string;
  lastMessageText: string;
  lastMessageSenderId: string;
  lastMessageAt?: Timestamp | null;
  createdAt?: Timestamp | null;
  updatedAt?: Timestamp | null;
}

export interface ChatMessage {
  id: string;
  chatId: string;
  senderId: string;
  senderName: string;
  messageType: "text" | "image" | "voice" | "call_event" | string;
  text: string;
  imageBase64?: string | null;
  voiceDurationSec?: number | null;
  participantIds: string[];
  isRead: boolean;
  createdAt?: Timestamp | null;
}

export interface CallSession {
  id: string;
  hostId: string;
  hostName: string;
  roomCode: string;
  callType: "audio" | "video" | "group" | string;
  status: "ringing" | "active" | "ended" | string;
  participantIds: string[];
  participantNames?: string[] | null;
  durationSec: number;
  createdAt?: Timestamp | null;
  updatedAt?: Timestamp | null;
}

export interface AdminRecord {
  id: string;
  userId: string;
  displayName: string;
  email: string;
  role: "super_admin" | "moderator" | string;
  grantedBy: string;
  createdAt?: Timestamp | null;
  updatedAt?: Timestamp | null;
}

