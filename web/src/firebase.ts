import { initializeApp, getApps, type FirebaseApp } from "firebase/app";
import { getAuth, GoogleAuthProvider, type Auth } from "firebase/auth";
import { getFirestore, type Firestore } from "firebase/firestore";
import { getStorage, type FirebaseStorage } from "firebase/storage";

const DEFAULT_PROJECT_ID = "gen-lang-client-0094828632";
const DEFAULT_DATABASE_ID = "ai-studio-android-pulsecas-9e6b52a5-7c12-482d-961e-c26729bf0f15";
const DEFAULT_SENDER_ID = "210855174891";
const RUNTIME_CONFIG_STORAGE_KEY = "pulsecast_web_firebase_config_v1";

export interface RuntimeFirebaseConfig {
  apiKey?: string;
  authDomain?: string;
  projectId?: string;
  storageBucket?: string;
  messagingSenderId?: string;
  appId?: string;
  databaseId?: string;
}

function loadRuntimeConfig(): RuntimeFirebaseConfig {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(RUNTIME_CONFIG_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? (parsed as RuntimeFirebaseConfig) : {};
  } catch {
    return {};
  }
}

export function saveRuntimeFirebaseConfig(config: RuntimeFirebaseConfig): void {
  if (typeof window === "undefined") return;
  const cleaned: RuntimeFirebaseConfig = {
    apiKey: config.apiKey?.trim() || undefined,
    authDomain: config.authDomain?.trim() || undefined,
    projectId: config.projectId?.trim() || undefined,
    storageBucket: config.storageBucket?.trim() || undefined,
    messagingSenderId: config.messagingSenderId?.trim() || undefined,
    appId: config.appId?.trim() || undefined,
    databaseId: config.databaseId?.trim() || undefined
  };
  window.localStorage.setItem(RUNTIME_CONFIG_STORAGE_KEY, JSON.stringify(cleaned));
}

export function clearRuntimeFirebaseConfig(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(RUNTIME_CONFIG_STORAGE_KEY);
}

export function parseFirebaseConfigSnippet(rawInput: string): RuntimeFirebaseConfig {
  const result: RuntimeFirebaseConfig = {};
  const keys: (keyof RuntimeFirebaseConfig)[] = [
    "apiKey",
    "authDomain",
    "projectId",
    "storageBucket",
    "messagingSenderId",
    "appId",
    "databaseId"
  ];
  for (const key of keys) {
    const match = rawInput.match(
      new RegExp(`["']?${key}["']?\\s*:\\s*["']([^"']+)["']`)
    );
    if (match?.[1]) {
      result[key] = match[1].trim();
    }
  }
  return result;
}

const runtimeOverride = loadRuntimeConfig();
export const hasRuntimeFirebaseOverride = Boolean(runtimeOverride.apiKey);

const resolvedProjectId =
  import.meta.env.VITE_FIREBASE_PROJECT_ID?.trim() ||
  runtimeOverride.projectId?.trim() ||
  DEFAULT_PROJECT_ID;

export const firebaseWebConfig = {
  apiKey:
    import.meta.env.VITE_FIREBASE_API_KEY?.trim() ||
    runtimeOverride.apiKey?.trim() ||
    "",
  authDomain:
    import.meta.env.VITE_FIREBASE_AUTH_DOMAIN?.trim() ||
    runtimeOverride.authDomain?.trim() ||
    `${resolvedProjectId}.firebaseapp.com`,
  projectId: resolvedProjectId,
  storageBucket:
    import.meta.env.VITE_FIREBASE_STORAGE_BUCKET?.trim() ||
    runtimeOverride.storageBucket?.trim() ||
    `${resolvedProjectId}.firebasestorage.app`,
  messagingSenderId:
    import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID?.trim() ||
    runtimeOverride.messagingSenderId?.trim() ||
    DEFAULT_SENDER_ID,
  appId:
    import.meta.env.VITE_FIREBASE_APP_ID?.trim() ||
    runtimeOverride.appId?.trim() ||
    "",
  databaseId:
    import.meta.env.VITE_FIRESTORE_DATABASE_ID?.trim() ||
    runtimeOverride.databaseId?.trim() ||
    DEFAULT_DATABASE_ID
};

export const hasWebFirebaseConfig = Boolean(
  firebaseWebConfig.apiKey && firebaseWebConfig.projectId
);

let appInstance: FirebaseApp | null = null;
let authInstance: Auth | null = null;
let dbInstance: Firestore | null = null;
let storageInstance: FirebaseStorage | null = null;

if (hasWebFirebaseConfig) {
  appInstance = getApps().length
    ? getApps()[0]
    : initializeApp({
        apiKey: firebaseWebConfig.apiKey,
        authDomain: firebaseWebConfig.authDomain,
        projectId: firebaseWebConfig.projectId,
        storageBucket: firebaseWebConfig.storageBucket,
        messagingSenderId: firebaseWebConfig.messagingSenderId,
        ...(firebaseWebConfig.appId ? { appId: firebaseWebConfig.appId } : {})
      });
  authInstance = getAuth(appInstance);
  dbInstance = getFirestore(appInstance, firebaseWebConfig.databaseId);
  storageInstance = getStorage(appInstance);
}

export const firebaseApp = appInstance;
export const auth = authInstance;
export const db = dbInstance;
export const storage = storageInstance;
export const googleProvider = new GoogleAuthProvider();
