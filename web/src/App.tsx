import { useEffect, useMemo, useState } from "react";
import { onAuthStateChanged, signOut, type User } from "firebase/auth";
import { Download, Film, LogOut, MessageSquare, PhoneCall, Radio, ShieldCheck, UserCircle } from "lucide-react";
import { auth, hasWebFirebaseConfig } from "./firebase";
import { callAudio } from "./callAudio";
import {
  createCallSession,
  createChatThread,
  declineCallSession,
  ensureUserAndCreatorInitialized,
  joinCallSession,
  observeAdmins,
  observeAllCallSessionsForAdmin,
  observeAllCreatorsForAdmin,
  observeAllUsersForAdmin,
  observeAllVideosForAdmin,
  observeCallSessions,
  observeChats,
  observeCreators,
  observeIsAdminDoc,
  observeMyVideos,
  observePublicVideos,
  observeUserAccount,
  sendMessage,
  toTimestampMillis,
  updateUserActivity
} from "./repository";
import { AuthView } from "./components/AuthView";
import { FeedView } from "./components/FeedView";
import { ChatsView } from "./components/ChatsView";
import { CallsView } from "./components/CallsView";
import { ProfileView } from "./components/ProfileView";
import { AdminView } from "./components/AdminView";
import { IncomingCallModal } from "./components/IncomingCallModal";
import { CreatorProfileModal } from "./components/CreatorProfileModal";
import { UserAvatar } from "./components/UserAvatar";
import type {
  AdminRecord,
  CallSession,
  ChatThread,
  CreatorProfile,
  PublishedVideo,
  UserAccount
} from "./types";

type TabId = "feed" | "chats" | "calls" | "profile" | "admin";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
}

function getInitialTabFromUrl(): TabId {
  if (typeof window === "undefined") return "feed";
  const tabParam = new URLSearchParams(window.location.search).get("tab")?.toLowerCase();
  if (tabParam === "chats" || tabParam === "calls" || tabParam === "profile" || tabParam === "admin") {
    return tabParam;
  }
  return "feed";
}

const BOOTSTRAPPED_ADMIN_EMAIL = "rsk755734@gmail.com";
const CALL_RING_WINDOW_MS = 3 * 60 * 1000;

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authReady, setAuthReady] = useState(!hasWebFirebaseConfig);
  const [activeTab, setActiveTab] = useState<TabId>(() => getInitialTabFromUrl());
  const [installPromptEvent, setInstallPromptEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [account, setAccount] = useState<UserAccount | null>(null);
  const [creators, setCreators] = useState<CreatorProfile[]>([]);
  const [publicVideos, setPublicVideos] = useState<PublishedVideo[]>([]);
  const [myVideos, setMyVideos] = useState<PublishedVideo[]>([]);
  const [chats, setChats] = useState<ChatThread[]>([]);
  const [callSessions, setCallSessions] = useState<CallSession[]>([]);
  const [activeCall, setActiveCall] = useState<CallSession | null>(null);
  const [dismissedCallIds, setDismissedCallIds] = useState<Set<string>>(() => new Set());
  const [audioBlocked, setAudioBlocked] = useState(false);
  const [selectedCreatorProfile, setSelectedCreatorProfile] = useState<CreatorProfile | null>(null);
  const [hasAdminDoc, setHasAdminDoc] = useState(false);
  const [adminVideos, setAdminVideos] = useState<PublishedVideo[]>([]);
  const [adminCreators, setAdminCreators] = useState<CreatorProfile[]>([]);
  const [adminCalls, setAdminCalls] = useState<CallSession[]>([]);
  const [adminUsers, setAdminUsers] = useState<UserAccount[]>([]);
  const [adminsList, setAdminsList] = useState<AdminRecord[]>([]);
  const [selectedVideo, setSelectedVideo] = useState<PublishedVideo | null>(null);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  const notify = (msg: string) => {
    setToast(msg);
  };

  useEffect(() => {
    if (!toast) return;
    const t = window.setTimeout(() => setToast(null), 3800);
    return () => window.clearTimeout(t);
  }, [toast]);

  useEffect(() => {
    const onBeforeInstallPrompt = (event: Event) => {
      event.preventDefault();
      setInstallPromptEvent(event as BeforeInstallPromptEvent);
    };
    const onAppInstalled = () => {
      setInstallPromptEvent(null);
      notify("PulseCast installed as an app.");
    };
    window.addEventListener("beforeinstallprompt", onBeforeInstallPrompt);
    window.addEventListener("appinstalled", onAppInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onBeforeInstallPrompt);
      window.removeEventListener("appinstalled", onAppInstalled);
    };
  }, []);

  useEffect(() => {
    const uninstallGesture = callAudio.installGestureUnlock();
    const unsubBlocked = callAudio.subscribeBlocked(setAudioBlocked);
    return () => {
      uninstallGesture();
      unsubBlocked();
      callAudio.stopIncomingRingtone();
      callAudio.stopOutgoingRingback();
    };
  }, []);

  useEffect(() => {
    if (!auth) {
      setAuthReady(true);
      return;
    }
    return onAuthStateChanged(auth, async (nextUser) => {
      setUser(nextUser);
      setAuthReady(true);
      if (nextUser) {
        try {
          await ensureUserAndCreatorInitialized(
            nextUser.uid,
            nextUser.displayName || "PulseCast Creator",
            nextUser.email || "",
            nextUser.photoURL
          );
        } catch (err) {
          notify(err instanceof Error ? err.message : "Could not initialize creator profile.");
        }
      }
    });
  }, []);

  const isBootstrappedAdmin = Boolean(
    user?.email?.toLowerCase() === BOOTSTRAPPED_ADMIN_EMAIL && user?.emailVerified
  );
  const isAdmin = hasAdminDoc || isBootstrappedAdmin;

  useEffect(() => {
    if (!user) {
      setAccount(null);
      setCreators([]);
      setPublicVideos([]);
      setMyVideos([]);
      setChats([]);
      setCallSessions([]);
      setHasAdminDoc(false);
      return;
    }

    const unsubs = [
      observeUserAccount(user.uid, setAccount, (e) => notify(e.message)),
      observeCreators(setCreators, (e) => notify(e.message)),
      observePublicVideos(setPublicVideos, (e) => notify(e.message)),
      observeMyVideos(user.uid, setMyVideos, (e) => notify(e.message)),
      observeChats(user.uid, setChats, (e) => notify(e.message)),
      observeCallSessions(user.uid, setCallSessions, (e) => notify(e.message)),
      observeIsAdminDoc(user.uid, setHasAdminDoc)
    ];

    const heartbeat = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        void updateUserActivity(user.uid, true).catch(() => {});
      }
    }, 60_000);
    const onVisibilityChange = () => {
      void updateUserActivity(user.uid, document.visibilityState === "visible").catch(() => {});
    };
    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      unsubs.forEach((fn) => fn());
      window.clearInterval(heartbeat);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [user?.uid]);

  useEffect(() => {
    if (!user || !isAdmin) {
      setAdminVideos([]);
      setAdminCreators([]);
      setAdminCalls([]);
      setAdminUsers([]);
      setAdminsList([]);
      if (activeTab === "admin") setActiveTab("feed");
      return;
    }

    const unsubs = [
      observeAllVideosForAdmin(setAdminVideos, (e) => notify(e.message)),
      observeAllCreatorsForAdmin(setAdminCreators, (e) => notify(e.message)),
      observeAllCallSessionsForAdmin(setAdminCalls, (e) => notify(e.message)),
      observeAllUsersForAdmin(setAdminUsers, (e) => notify(e.message)),
      observeAdmins(setAdminsList, (e) => notify(e.message))
    ];
    return () => {
      unsubs.forEach((fn) => fn());
    };
  }, [user?.uid, isAdmin]);

  // Keep activeCall synced with live Firestore call_sessions updates
  useEffect(() => {
    if (!activeCall) return;
    const updated = callSessions.find(
      (s) => s.id === activeCall.id || s.roomCode === activeCall.roomCode
    );
    if (!updated) return;
    if (updated.status === "ended") {
      setActiveCall(null);
      notify(`Call ${updated.roomCode} ended.`);
      return;
    }
    setActiveCall(updated);
  }, [callSessions, activeCall?.id, activeCall?.roomCode]);

  const incomingCall = useMemo(() => {
    if (!user || activeCall) return null;
    const nowMs = Date.now();
    return (
      callSessions.find((s) => {
        if (s.status !== "ringing") return false;
        if (!s.hostId || s.hostId === user.uid) return false;
        if (!s.participantIds.includes(user.uid)) return false;
        if (dismissedCallIds.has(s.id) || dismissedCallIds.has(s.roomCode)) return false;
        const refMs = toTimestampMillis(s.updatedAt) || toTimestampMillis(s.createdAt);
        if (refMs > 0 && nowMs - refMs > CALL_RING_WINDOW_MS) return false;
        return true;
      }) || null
    );
  }, [user?.uid, activeCall, callSessions, dismissedCallIds]);

  // Trigger incoming ringtone & outgoing ringback
  useEffect(() => {
    if (incomingCall) {
      callAudio.startIncomingRingtone(incomingCall.roomCode || incomingCall.id);
    } else {
      callAudio.stopIncomingRingtone();
    }
  }, [incomingCall?.id, incomingCall?.roomCode]);

  useEffect(() => {
    if (user && activeCall && activeCall.status === "ringing" && activeCall.hostId === user.uid) {
      callAudio.startOutgoingRingback(activeCall.roomCode || activeCall.id);
    } else {
      callAudio.stopOutgoingRingback();
    }
  }, [user?.uid, activeCall?.id, activeCall?.roomCode, activeCall?.status, activeCall?.hostId]);

  if (!authReady) {
    return (
      <div className="auth-page">
        <div style={{ color: "var(--text-secondary)" }}>Connecting to PulseCast...</div>
      </div>
    );
  }

  if (!user) {
    return <AuthView />;
  }

  const currentCreator = creators.find((c) => c.userId === user.uid) || null;
  const resolvedName =
    currentCreator?.displayName || account?.displayName || user.displayName || "Creator";

  const handleSignOut = async () => {
    if (!auth) return;
    callAudio.stopIncomingRingtone();
    callAudio.stopOutgoingRingback();
    await updateUserActivity(user.uid, false).catch(() => {});
    await signOut(auth);
  };

  const handleInstallPwa = async () => {
    if (!installPromptEvent) return;
    await installPromptEvent.prompt();
    await installPromptEvent.userChoice.catch(() => null);
    setInstallPromptEvent(null);
  };

  const handleStartCall = async (
    callType: "audio" | "video" | "group",
    targetCreator?: CreatorProfile | null
  ) => {
    void callAudio.unlock();
    try {
      const invitedIds = targetCreator ? [targetCreator.userId] : [];
      const invitedNames = targetCreator ? [targetCreator.displayName] : [];
      const session = await createCallSession(
        user.uid,
        resolvedName,
        callType,
        invitedIds,
        invitedNames
      );
      setActiveCall(session);
      setActiveTab("calls");

      if (targetCreator) {
        const existingThread = chats.find(
          (t) =>
            !t.isGroup &&
            t.participantIds.length === 2 &&
            t.participantIds.includes(user.uid) &&
            t.participantIds.includes(targetCreator.userId)
        );
        const chatId =
          existingThread?.id ||
          (await createChatThread({
            uid: user.uid,
            creatorName: resolvedName,
            title: targetCreator.displayName || `@${targetCreator.handle}`,
            isGroup: false,
            participantIds: [targetCreator.userId],
            participantNames: [targetCreator.displayName]
          }));
        await sendMessage({
          chatId,
          uid: user.uid,
          senderName: resolvedName,
          participantIds: [user.uid, targetCreator.userId],
          messageType: "call_event",
          text: `Started ${callType.toUpperCase()} call · Room ${session.roomCode}`
        }).catch(() => {});
      }

      notify(
        targetCreator
          ? `Ringing ${targetCreator.displayName} (${session.roomCode})...`
          : `Call room ${session.roomCode} is live.`
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : "Could not start call.");
    }
  };

  const handleAnswerCall = async (session: CallSession) => {
    void callAudio.unlock();
    callAudio.stopIncomingRingtone();
    const code = session.roomCode || session.id;
    setDismissedCallIds((prev) => {
      const next = new Set(prev);
      next.add(session.id);
      next.add(code);
      return next;
    });
    setActiveCall({ ...session, status: "active" });
    setActiveTab("calls");
    try {
      const joined = await joinCallSession(code, user.uid, resolvedName);
      setActiveCall(joined);
    } catch (err) {
      setActiveCall(null);
      notify(err instanceof Error ? err.message : "Call is no longer active.");
    }
  };

  const handleDeclineCall = async (session: CallSession) => {
    callAudio.stopIncomingRingtone();
    const code = session.roomCode || session.id;
    setDismissedCallIds((prev) => {
      const next = new Set(prev);
      next.add(session.id);
      next.add(code);
      return next;
    });
    if (activeCall && (activeCall.id === session.id || activeCall.roomCode === code)) {
      setActiveCall(null);
    }
    try {
      await declineCallSession(code);
      notify("Call declined.");
    } catch (err) {
      notify(err instanceof Error ? err.message : "Could not decline call.");
    }
  };

  const navItems: { id: TabId; label: string; icon: typeof Film }[] = [
    { id: "feed", label: "Feed", icon: Film },
    { id: "chats", label: "Chats", icon: MessageSquare },
    { id: "calls", label: "Calls", icon: PhoneCall },
    { id: "profile", label: "Profile", icon: UserCircle },
    ...(isAdmin ? [{ id: "admin" as TabId, label: "Admin", icon: ShieldCheck }] : [])
  ];

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <div className="brand-header">
            <div className="brand-logo">
              <Radio size={20} />
            </div>
            <div>
              <div className="brand-name">PulseCast</div>
              <div className="brand-sub">Web Studio</div>
            </div>
          </div>

          <nav className="nav-list">
            {navItems.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                type="button"
                className={`nav-item ${activeTab === id ? "active" : ""}`}
                onClick={() => setActiveTab(id)}
              >
                <Icon size={19} />
                <span>{label}</span>
              </button>
            ))}
          </nav>
        </div>

        <div className="sidebar-footer">
          <div className="mini-user">
            <UserAvatar
              displayName={resolvedName}
              photoUrl={currentCreator?.photoUrl || account?.photoUrl || user.photoURL}
              avatarBase64={currentCreator?.avatarBase64 || account?.avatarBase64}
              isOnline={true}
              size={38}
            />
            <div style={{ minWidth: 0 }}>
              <div
                style={{
                  fontWeight: 600,
                  fontSize: "0.88rem",
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis"
                }}
              >
                {resolvedName}
              </div>
              <div style={{ fontSize: "0.74rem", color: "var(--text-secondary)" }}>
                @{currentCreator?.handle || "creator"}
              </div>
            </div>
          </div>
          {installPromptEvent && (
            <button
              type="button"
              className="btn btn-primary btn-sm"
              style={{ marginBottom: 8, width: "100%" }}
              onClick={handleInstallPwa}
            >
              <Download size={15} />
              Install App
            </button>
          )}
          <button type="button" className="btn btn-secondary btn-sm" onClick={handleSignOut}>
            <LogOut size={15} />
            Sign Out
          </button>
        </div>
      </aside>

      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
        <header className="mobile-topbar">
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div className="brand-logo" style={{ width: 32, height: 32, borderRadius: 10 }}>
              <Radio size={16} />
            </div>
            <span className="brand-name" style={{ fontSize: "1.05rem" }}>
              PulseCast
            </span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            {installPromptEvent && (
              <button type="button" className="btn btn-primary btn-sm" onClick={handleInstallPwa}>
                <Download size={15} />
                Install
              </button>
            )}
            <button type="button" className="btn btn-ghost btn-sm" onClick={handleSignOut}>
              <LogOut size={16} />
            </button>
          </div>
        </header>

        <main className="main-area">
          {activeTab === "feed" && (
            <FeedView
              uid={user.uid}
              authorName={resolvedName}
              videos={publicVideos}
              selectedVideo={selectedVideo}
              onSelectVideo={setSelectedVideo}
              showUploadModal={showUploadModal}
              onOpenUploadModal={setShowUploadModal}
              onNotify={notify}
            />
          )}

          {activeTab === "chats" && (
            <ChatsView
              uid={user.uid}
              creatorName={resolvedName}
              chats={chats}
              creators={creators}
              onNotify={notify}
            />
          )}

          {activeTab === "calls" && (
            <CallsView
              uid={user.uid}
              userName={resolvedName}
              sessions={callSessions}
              creators={creators}
              activeCall={activeCall}
              onActiveCallChange={setActiveCall}
              onStartCall={handleStartCall}
              onAnswerCall={handleAnswerCall}
              onDeclineCall={handleDeclineCall}
              onOpenCreatorProfile={setSelectedCreatorProfile}
              onNotify={notify}
            />
          )}

          {activeTab === "profile" && (
            <ProfileView
              uid={user.uid}
              email={user.email || ""}
              photoUrl={user.photoURL}
              fallbackDisplayName={user.displayName || "Creator"}
              account={account}
              creator={currentCreator}
              myVideos={myVideos}
              onWatchVideo={(video) => {
                setSelectedVideo(video);
                setActiveTab("feed");
              }}
              onOpenUpload={() => {
                setActiveTab("feed");
                setShowUploadModal(true);
              }}
              onSignOut={handleSignOut}
              onNotify={notify}
            />
          )}

          {activeTab === "admin" && isAdmin && (
            <AdminView
              currentUserId={user.uid}
              currentUserEmail={user.email || ""}
              videos={adminVideos}
              creators={adminCreators}
              calls={adminCalls}
              users={adminUsers}
              admins={adminsList}
              onNotify={notify}
            />
          )}
        </main>
      </div>

      <nav
        className="mobile-bottom-nav"
        style={{ gridTemplateColumns: `repeat(${navItems.length}, 1fr)` }}
      >
        {navItems.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            className={`mobile-nav-btn ${activeTab === id ? "active" : ""}`}
            onClick={() => setActiveTab(id)}
          >
            <Icon size={20} />
            <span>{label}</span>
          </button>
        ))}
      </nav>

      {incomingCall && (
        <IncomingCallModal
          session={incomingCall}
          callerProfile={creators.find((c) => c.userId === incomingCall.hostId) || null}
          audioBlocked={audioBlocked}
          onUnlockAudio={() => {
            void callAudio.unlock().then((ok) => {
              if (ok) {
                const key = incomingCall.roomCode || incomingCall.id;
                callAudio.stopIncomingRingtone();
                callAudio.startIncomingRingtone(key);
              }
            });
          }}
          onAnswer={() => void handleAnswerCall(incomingCall)}
          onDecline={() => void handleDeclineCall(incomingCall)}
        />
      )}

      {selectedCreatorProfile && (
        <CreatorProfileModal
          creator={selectedCreatorProfile}
          isCurrentUser={selectedCreatorProfile.userId === user.uid}
          videos={publicVideos.filter((v) => v.authorId === selectedCreatorProfile.userId)}
          onClose={() => setSelectedCreatorProfile(null)}
          onMessage={() => {
            setSelectedCreatorProfile(null);
            setActiveTab("chats");
          }}
          onAudioCall={(creator) => {
            setSelectedCreatorProfile(null);
            void handleStartCall("audio", creator);
          }}
          onVideoCall={(creator) => {
            setSelectedCreatorProfile(null);
            void handleStartCall("video", creator);
          }}
          onWatchVideo={(video) => {
            setSelectedCreatorProfile(null);
            setSelectedVideo(video);
            setActiveTab("feed");
          }}
          onEditOwnProfile={() => {
            setSelectedCreatorProfile(null);
            setActiveTab("profile");
          }}
        />
      )}

      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
export default App;
