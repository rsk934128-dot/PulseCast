import { useState } from "react";
import { signInWithPopup } from "firebase/auth";
import { AlertTriangle, MessageSquare, PhoneCall, Radio, ShieldCheck, Video } from "lucide-react";
import { auth, firebaseWebConfig, googleProvider, hasWebFirebaseConfig } from "../firebase";

interface AuthViewProps {
  onSignedIn?: () => void;
}

export function AuthView({ onSignedIn }: AuthViewProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleGoogleSignIn = async () => {
    if (!auth) {
      setError("Firebase Web configuration is missing. Set VITE_FIREBASE_API_KEY and VITE_FIREBASE_APP_ID.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await signInWithPopup(auth, googleProvider);
      onSignedIn?.();
    } catch (err: unknown) {
      const code = typeof err === "object" && err && "code" in err ? String((err as { code: string }).code) : "";
      const message = err instanceof Error ? err.message : "Failed to sign in with Google.";
      if (code === "auth/unauthorized-domain") {
        setError(
          `This domain (${window.location.hostname}) is not authorized in Firebase Auth. Add it in Firebase Console -> Authentication -> Settings -> Authorized domains.`
        );
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <span className="brand-pill">
          <Radio size={15} />
          PulseCast Studio · Web Companion
        </span>

        <h1 className="auth-title">Create, Stream & Connect Anywhere</h1>
        <p className="auth-subtitle">
          Sign in with the same Google account you use on the PulseCast Android app. All videos,
          chats, creator profiles, and call rooms stay synced in real time.
        </p>

        <div className="feature-grid">
          <div className="feature-tile">
            <Video size={18} />
            <div>
              <strong>Video Publishing</strong>
              <span>Watch streams and upload MP4/WebM clips directly to Firebase Storage.</span>
            </div>
          </div>
          <div className="feature-tile">
            <MessageSquare size={18} />
            <div>
              <strong>Real-Time Chats</strong>
              <span>1-on-1 and group conversations synced across Android and Web.</span>
            </div>
          </div>
          <div className="feature-tile">
            <PhoneCall size={18} />
            <div>
              <strong>Studio Call Rooms</strong>
              <span>Start or join room-coded audio and camera sessions in your browser.</span>
            </div>
          </div>
          <div className="feature-tile">
            <ShieldCheck size={18} />
            <div>
              <strong>Zero-Trust Sync</strong>
              <span>Protected by Firebase Auth & Firestore security rules ({firebaseWebConfig.projectId}).</span>
            </div>
          </div>
        </div>

        {!hasWebFirebaseConfig && (
          <div className="setup-banner">
            <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 700, marginBottom: 6 }}>
              <AlertTriangle size={18} />
              Vercel / Web Setup Required
            </div>
            Add a Web App in Firebase Console (Project <code>{firebaseWebConfig.projectId}</code>)
            and set <code>VITE_FIREBASE_API_KEY</code> and <code>VITE_FIREBASE_APP_ID</code> in
            Vercel Environment Variables (or <code>web/.env</code> locally), then redeploy.
          </div>
        )}

        {error && (
          <div
            style={{
              background: "rgba(239, 68, 68, 0.14)",
              border: "1px solid rgba(239, 68, 68, 0.35)",
              color: "#fecaca",
              borderRadius: 12,
              padding: "12px 14px",
              marginBottom: 16,
              fontSize: "0.86rem",
              lineHeight: 1.45
            }}
          >
            {error}
          </div>
        )}

        <button
          type="button"
          className="btn btn-primary"
          style={{ width: "100%", height: 50, fontSize: "1rem" }}
          onClick={handleGoogleSignIn}
          disabled={loading || !hasWebFirebaseConfig}
        >
          {loading ? "Signing in with Google..." : "Continue with Google"}
        </button>
      </div>
    </div>
  );
}
