import { useEffect, useState } from "react";
import { doc, onSnapshot } from "firebase/firestore";
import { db } from "../firebase";
import { resolveIsUserOnline } from "../repository";
import type { CreatorProfile, UserAccount } from "../types";

export interface UseUserResult {
  user: UserAccount | null;
  creator: CreatorProfile | null;
  displayName: string;
  photoUrl: string | null;
  avatarBase64: string | null;
  isOnline: boolean;
  loading: boolean;
}

/**
 * Subscribes in real time to a user's document in the Firestore `users` collection
 * (and falls back to `creators/{userId}` when Firestore PII rules restrict cross-user
 * reads on `users`), returning live photoUrl, avatarBase64, displayName, and online status.
 */
export function useUser(
  userId?: string | null,
  fallbackProfile?: CreatorProfile | null,
  fallbackName?: string | null
): UseUserResult {
  const [userDoc, setUserDoc] = useState<UserAccount | null>(null);
  const [creatorDoc, setCreatorDoc] = useState<CreatorProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(Boolean(userId && db));

  useEffect(() => {
    const cleanUid = userId?.trim();
    if (!cleanUid || !db) {
      setUserDoc(null);
      setCreatorDoc(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    let userSettled = false;
    let creatorSettled = false;
    const markSettled = () => {
      if (userSettled || creatorSettled) {
        setLoading(false);
      }
    };

    const unsubUser = onSnapshot(
      doc(db, "users", cleanUid),
      (snap) => {
        userSettled = true;
        setUserDoc(snap.exists() ? ({ id: snap.id, ...snap.data() } as UserAccount) : null);
        markSettled();
      },
      () => {
        // Cross-user reads on `users` may be denied by PII rules; rely on `creators` snapshot.
        userSettled = true;
        setUserDoc(null);
        markSettled();
      }
    );

    const unsubCreator = onSnapshot(
      doc(db, "creators", cleanUid),
      (snap) => {
        creatorSettled = true;
        setCreatorDoc(
          snap.exists() ? ({ id: snap.id, ...snap.data() } as CreatorProfile) : null
        );
        markSettled();
      },
      () => {
        creatorSettled = true;
        setCreatorDoc(null);
        markSettled();
      }
    );

    return () => {
      unsubUser();
      unsubCreator();
    };
  }, [userId]);

  const resolvedCreator = creatorDoc || fallbackProfile || null;
  const photoUrl =
    userDoc?.photoUrl?.trim() ||
    resolvedCreator?.photoUrl?.trim() ||
    null;
  const avatarBase64 =
    userDoc?.avatarBase64?.trim() ||
    resolvedCreator?.avatarBase64?.trim() ||
    null;
  const displayName =
    userDoc?.displayName?.trim() ||
    resolvedCreator?.displayName?.trim() ||
    fallbackName?.trim() ||
    "PulseCast User";

  const isOnline =
    userDoc != null
      ? resolveIsUserOnline(userDoc.isOnline, userDoc.lastActiveAt, userDoc.updatedAt)
      : resolvedCreator != null
      ? resolveIsUserOnline(
          resolvedCreator.isOnline,
          resolvedCreator.lastActiveAt,
          resolvedCreator.updatedAt
        )
      : false;

  return {
    user: userDoc,
    creator: resolvedCreator,
    displayName,
    photoUrl,
    avatarBase64,
    isOnline,
    loading
  };
}
