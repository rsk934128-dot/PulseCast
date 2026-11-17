import { initializeApp, getApps, type FirebaseApp } from "firebase/app";
import { getAuth, GoogleAuthProvider, type Auth } from "firebase/auth";
import { getFirestore, type Firestore } from "firebase/firestore";
import { getStorage, type FirebaseStorage } from "firebase/storage";

const DEFAULT_PROJECT_ID = "gen-lang-client-0094828632";
const DEFAULT_DATABASE_ID = "ai-studio-android-pulsecas-9e6b52a5-7c12-482d-961e-c26729bf0f15";

export const firebaseWebConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY?.trim() || "",
  authDomain:
    import.meta.env.VITE_FIREBASE_AUTH_DOMAIN?.trim() || `${DEFAULT_PROJECT_ID}.firebaseapp.com`,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID?.trim() || DEFAULT_PROJECT_ID,
  storageBucket:
    import.meta.env.VITE_FIREBASE_STORAGE_BUCKET?.trim() || `${DEFAULT_PROJECT_ID}.firebasestorage.app`,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID?.trim() || "210855174891",
  appId: import.meta.env.VITE_FIREBASE_APP_ID?.trim() || "",
  databaseId: import.meta.env.VITE_FIRESTORE_DATABASE_ID?.trim() || DEFAULT_DATABASE_ID
};

export const hasWebFirebaseConfig = Boolean(firebaseWebConfig.apiKey && firebaseWebConfig.appId);

let appInstance: FirebaseApp | null = null;
let authInstance: Auth | null = null;
let dbInstance: Firestore | null = null;
let storageInstance: FirebaseStorage | null = null;

if (hasWebFirebaseConfig) {
  appInstance = getApps().length ? getApps()[0] : initializeApp({
    apiKey: firebaseWebConfig.apiKey,
    authDomain: firebaseWebConfig.authDomain,
    projectId: firebaseWebConfig.projectId,
    storageBucket: firebaseWebConfig.storageBucket,
    messagingSenderId: firebaseWebConfig.messagingSenderId,
    appId: firebaseWebConfig.appId
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
