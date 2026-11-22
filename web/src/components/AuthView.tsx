import { useState } from "react";
import { signInWithPopup } from "firebase/auth";
import {
  AlertTriangle,
  ExternalLink,
  KeyRound,
  MessageSquare,
  PhoneCall,
  Radio,
  RotateCcw,
  ShieldCheck,
  Video
} from "lucide-react";
import {
  auth,
  clearRuntimeFirebaseConfig,
  firebaseWebConfig,
  googleProvider,
  hasRuntimeFirebaseOverride,
  hasWebFirebaseConfig,
  parseFirebaseConfigSnippet,
  saveRuntimeFirebaseConfig
} from "../firebase";

interface AuthViewProps {
  onSignedIn?: () => void;
}

export function AuthView({ onSignedIn }: AuthViewProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showManualSetup, setShowManualSetup] = useState(!hasWebFirebaseConfig);
  const [snippetInput, setSnippetInput] = useState("");
  const [apiKeyInput, setApiKeyInput] = useState(firebaseWebConfig.apiKey);
  const [appIdInput, setAppIdInput] = useState(firebaseWebConfig.appId);

  const firebaseSettingsUrl = `https://console.firebase.google.com/project/${firebaseWebConfig.projectId}/settings/general`;

  const handleSnippetChange = (text: string) => {
    setSnippetInput(text);
    const parsed = parseFirebaseConfigSnippet(text);
    if (parsed.apiKey) setApiKeyInput(parsed.apiKey);
    if (parsed.appId) setAppIdInput(parsed.appId);
  };

  const handleSaveRuntimeConfig = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = snippetInput.trim() ? parseFirebaseConfigSnippet(snippetInput) : {};
    const finalApiKey = (apiKeyInput || parsed.apiKey || "").trim();
    const finalAppId = (appIdInput || parsed.appId || "").trim();
    if (!finalApiKey) {
      setError("Paste your Firebase Web App snippet or enter VITE_FIREBASE_API_KEY.");
      return;
    }
    saveRuntimeFirebaseConfig({
      ...parsed,
      apiKey: finalApiKey,
      appId: finalAppId || undefined
    });
    window.location.reload();
  };

  const handleResetRuntimeConfig = () => {
    clearRuntimeFirebaseConfig();
    window.location.reload();
  };

  const handleGoogleSignIn = async () => {
    if (!auth) {
      setError("Firebase Web configuration is missing. Enter your Firebase Web config below or set Vercel environment variables.");
      setShowManualSetup(true);
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
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, fontWeight: 700, marginBottom: 6 }}>
              <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                <AlertTriangle size={18} />
                Vercel / Web Setup Required
              </span>
              <a
                href={firebaseSettingsUrl}
                target="_blank"
                rel="noreferrer"
                style={{
                  color: "var(--accent-cyan)",
                  fontSize: "0.78rem",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 4,
                  textDecoration: "none"
                }}
              >
                Firebase Settings
                <ExternalLink size={13} />
              </a>
            </div>
            Add a Web App in Firebase Console (Project <code>{firebaseWebConfig.projectId}</code>)
            and set <code>VITE_FIREBASE_API_KEY</code> and <code>VITE_FIREBASE_APP_ID</code> in
            Vercel Environment Variables (or paste them below to connect this browser immediately).
          </div>
        )}

        {showManualSetup && (
          <form
            onSubmit={handleSaveRuntimeConfig}
            style={{
              background: "rgba(15, 23, 42, 0.72)",
              border: "1px solid var(--border-subtle)",
              borderRadius: 14,
              padding: 14,
              marginBottom: 16,
              display: "flex",
              flexDirection: "column",
              gap: 10
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: "0.84rem", fontWeight: 600 }}>
              <KeyRound size={15} />
              Quick Connect (Optional Browser Override)
            </div>
            <textarea
              className="textarea"
              rows={3}
              placeholder='Paste Firebase snippet (const firebaseConfig = { apiKey: "...", appId: "..." })'
              value={snippetInput}
              onChange={(e) => handleSnippetChange(e.target.value)}
            />
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
              <input
                className="input"
                placeholder="VITE_FIREBASE_API_KEY"
                value={apiKeyInput}
                onChange={(e) => setApiKeyInput(e.target.value)}
              />
              <input
                className="input"
                placeholder="VITE_FIREBASE_APP_ID"
                value={appIdInput}
                onChange={(e) => setAppIdInput(e.target.value)}
              />
            </div>
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              {hasRuntimeFirebaseOverride && (
                <button type="button" className="btn btn-ghost btn-sm" onClick={handleResetRuntimeConfig}>
                  <RotateCcw size={14} />
                  Reset
                </button>
              )}
              <button type="submit" className="btn btn-secondary btn-sm">
                Save & Connect
              </button>
            </div>
          </form>
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

        {hasWebFirebaseConfig && !showManualSetup && (
          <div style={{ textAlign: "center", marginTop: 10 }}>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => setShowManualSetup(true)}
            >
              Configure Firebase Web keys
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
