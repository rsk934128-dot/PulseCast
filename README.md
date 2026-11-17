<div align="center">

![PulseCast Hero Banner](docs/images/pulsecast_hero.jpg)

# PulseCast

**Audio-Video Calling · Real-Time Messaging · Creator Video Publishing**

![Platform](https://img.shields.io/badge/Platform-Android%20(API%2024%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Media3 ExoPlayer](https://img.shields.io/badge/Media3-ExoPlayer%201.11.1-FF6D00?style=for-the-badge&logo=youtube&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%7C%20Firestore%20%7C%20Storage-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)

</div>

---

## Overview

**PulseCast** is a modern native Android creator platform built with **Kotlin** and **Jetpack Compose (Material 3)**. It unifies four core experiences inside an adaptive single-activity shell (`NavigationBar` on compact screens and `NavigationRail` on tablets/foldables):

1. **Creator Video Feed & Publishing** — Browse categorized public clips (`Tech`, `Studio`, `Podcast`, `Music`, `Vlog`, `Live`), watch streams with a custom **Media3 ExoPlayer** player, like and comment in real time, and upload new videos via a dedicated **Firebase Storage + Firestore** upload form.
2. **Real-Time Messaging** — Searchable conversation inbox (`ChatListScreen`), 1-to-1 and group chat rooms, text messages, image sharing, voice note recording, and live read receipts.
3. **Audio & Video Calling** — Create or join room-coded audio, video, and group call sessions with CameraX preview and runtime permission management.
4. **Creator Studio & User Profile** — View your Google-authenticated profile, follower-facing `@handle` and bio, upload metrics, preference toggles, and manage your uploaded **Public** and **Private Draft** videos.

---

## Interface Showcase

<div align="center">

![PulseCast UI Showcase](docs/images/pulsecast_showcase.jpg)

*From left to right: Video Feed & ExoPlayer, Real-Time Chats, Audio/Video Calling, and Creator Studio Profile.*

</div>

### Bundled Studio Artwork

| Authentication Hero | Studio Cover Art | Stage Cover Art |
| :---: | :---: | :---: |
| ![Auth Hero](app/src/main/res/drawable/img_auth_hero.jpg) | ![Studio Thumb](app/src/main/res/drawable/img_video_thumb_1.jpg) | ![Stage Thumb](app/src/main/res/drawable/img_video_thumb_2.jpg) |
| Google Sign-In landing banner | Default studio video cover | Default stage video cover |

---

## Architecture & Cloud Data Flow

<div align="center">

![PulseCast Cloud Architecture](docs/images/pulsecast_architecture.jpg)

</div>

PulseCast follows **MVVM + Repository** architecture with unidirectional state flow (`StateFlow` + `collectAsStateWithLifecycle`) and zero-trust Firebase security boundaries:

- **Authentication (`GoogleAuthManager`, `AuthScreen`)**: Uses **Jetpack Credential Manager** (`GetSignInWithGoogleOption` & silent `GetGoogleIdOption`) paired with **Firebase Authentication**. All Firestore listeners and writes are strictly gated behind an authenticated user session.
- **Database (`PulseCastRepository`, `firestore.rules`)**: Real-time `.snapshots()` flows backed by Cloud Firestore (named database configured in `firebase_applet_config.xml`) with schema-validated security rules (`users`, `creators`, `videos`, `likes`, `comments`, `chats`, `messages`, `call_sessions`).
- **Media Storage & Playback (`FirebaseStorageVideoUploader`, `PulseCastVideoPlayer`)**:
  - Local clips selected via zero-permission Android Photo Picker (`PickVisualMedia`) or recorded via `CaptureVideo` + `FileProvider` stream to **Firebase Storage** (`videos/{uid}/{videoId}.{ext}`) with live progress reporting before persisting metadata to Firestore.
  - Playback is powered by **Media3 ExoPlayer** with buffering states, seekbar scrubbing, playback speed (`1.0x`, `1.5x`, `2.0x`), mute toggle, error retry, and lifecycle pause/release.
- **Runtime Permissions (`PermissionManager`)**: Reusable **Accompanist Permissions** wrapper handling rationale cards, runtime prompts, and permanent-denial settings recovery for `CAMERA` and `RECORD_AUDIO`.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & UI** | Kotlin `2.2.10`, Jetpack Compose BOM `2024.09.00`, Material 3, Edge-to-Edge |
| **Media & Camera** | Jetpack Media3 ExoPlayer `1.11.1`, CameraX `1.5.0`, MediaMetadataRetriever, MediaRecorder |
| **Backend & Cloud** | Firebase BoM `34.17.0` (Firebase Auth, Cloud Firestore, Firebase Storage) |
| **Identity** | AndroidX Credential Manager `1.5.0`, Google Identity Services (`googleid`) |
| **Permissions & Images** | Accompanist Permissions `0.37.3`, Coil Compose `2.7.0` |
| **Testing** | JUnit 4, Robolectric `4.16.1`, Compose UI Test, Firebase Rules Unit Testing (`node:test`) |

---

## Project Structure

```text
├── app/src/main/java/com/example/
│   ├── MainActivity.kt                     # Adaptive NavigationBar / NavigationRail + Auth gate
│   ├── auth/
│   │   └── GoogleAuthManager.kt            # Credential Manager + Firebase Auth sign-in/sign-out
│   ├── data/
│   │   ├── PulseCastModels.kt              # Firestore entity models (@DocumentId, @ServerTimestamp)
│   │   ├── PulseCastRepository.kt          # Realtime flows, batch writes & Storage upload pipeline
│   │   └── FirestoreErrorHandler.kt        # Structured JSON Firestore error logging
│   ├── media/
│   │   ├── MediaHelpers.kt                 # Thumbnail extraction, file metadata, voice recorder
│   │   ├── PulseCastVideoPlayer.kt         # Reusable Media3 ExoPlayer Compose player
│   │   └── VideoStorageUploader.kt         # Firebase Storage upload/cleanup implementation
│   ├── permissions/
│   │   └── PermissionManager.kt            # Accompanist camera & microphone permission wrapper
│   └── ui/
│       ├── PulseCastViewModel.kt           # StateFlows & UI actions
│       └── screens/
│           ├── AuthScreen.kt               # Google Sign-In screen
│           ├── VideoFeedScreen.kt          # Video feed, category filter & VideoDetailScreen
│           ├── VideoUploadForm.kt          # Firebase Storage + Firestore upload form
│           ├── ChatListScreen.kt           # Searchable conversation list
│           ├── MessagingScreen.kt          # Chat threads, media & voice messaging
│           ├── CallsScreen.kt              # Audio/video/group call rooms & CameraX preview
│           ├── StudioProfileScreen.kt      # User profile, stats & uploaded videos manager
│           └── StudioProfileComponents.kt  # Profile header, stat pills & video list cards
├── docs/images/                            # README showcase visuals
├── firestore.rules                         # Zero-trust Firestore security rules
├── storage.rules                           # Owner-scoped Firebase Storage rules
├── firebase-blueprint.json                 # Firestore schema specification
└── firestore.test.js                       # Node emulator security rules test suite
```

---

## Build & Testing

### 1. Firebase Console Prerequisites
- **Firestore & Google Sign-In**: Configured via `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`.
- **Firebase Storage**: Ensure **Firebase Storage** is enabled in the Firebase Console for the project and apply the security rules defined in `storage.rules` so authenticated creators can upload files to `videos/{userId}/{fileName}`.

### 2. Build the Android App
```bash
gradle assembleDebug
```

### 3. Run Unit & Robolectric UI Tests
```bash
gradle :app:testDebugUnitTest
```

### 4. Run Firestore Security Rules Tests (with Firebase Emulators)
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

---

## Web Companion App & Vercel Hosting (`web/`)

The `web/` directory provides a responsive **React + TypeScript + Vite** web companion synced to the exact same Firebase project (`gen-lang-client-0094828632`), Cloud Firestore database, Firebase Storage bucket, and Google Sign-In accounts as the Android app.

### Deploy to Vercel
1. Push this repository to GitHub.
2. In **Vercel**, import the repository and set **Root Directory** to `web`.
3. Add the Firebase Web App environment variables from `web/.env.example` (`VITE_FIREBASE_API_KEY` and `VITE_FIREBASE_APP_ID`, plus optional overrides) under **Project Settings → Environment Variables**.
4. In **Firebase Console → Authentication → Settings → Authorized domains**, add your Vercel deployment domain (e.g., `your-app.vercel.app`).
5. Run locally anytime with:
```bash
cd web
npm install
npm run dev
```

---

## GitHub Actions Android APK Release

The repository includes an automated workflow at `.github/workflows/android-release.yml` that runs unit tests, builds the Android APK, and publishes downloadable assets directly to **GitHub Releases**.

### Option 1: Publish a Release Immediately (Zero Setup)
1. Push your project to GitHub.
2. Either:
   - **Tag push**: `git tag v1.0.0 && git push origin v1.0.0`
   - **Manual trigger**: Open **Actions → Android APK Release → Run workflow** in GitHub (optionally enter a tag such as `v1.0.0`).
3. Once the workflow finishes, open **Releases** in your GitHub repository and download `PulseCast-<version>-debug.apk` (signed with the repository's pre-registered `debug.keystore.base64` so Firebase Google Sign-In works immediately).

### Option 2: Publish Production-Signed Release APK & AAB (Optional)
If you want an additional release-signed `.apk` and `.aab` for distribution or Google Play:
1. Generate an upload keystore locally with alias `upload`:
   ```bash
   keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   base64 -w 0 my-upload-key.jks
   ```
2. Add these three **Repository Secrets** in GitHub (**Settings → Secrets and variables → Actions**):
   - `RELEASE_KEYSTORE_BASE64`: Base64 output of `my-upload-key.jks`
   - `STORE_PASSWORD`: Keystore password
   - `KEY_PASSWORD`: Key password
3. Copy the SHA-1 and SHA-256 fingerprints (`keytool -list -v -keystore my-upload-key.jks -alias upload`) into **Firebase Console → Project Settings → Your Android App (`com.aistudio.pulsecast.vktmrx`)** so Google Sign-In works in the release build as well.
4. Trigger the workflow again — GitHub Releases will automatically attach `PulseCast-<version>-release.apk`, `PulseCast-<version>-release.aab`, `PulseCast-<version>-debug.apk`, and `SHA256SUMS.txt`.


