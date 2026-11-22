package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private const val TAG = "PulseCastAuth"
private const val EXPECTED_GOOGLE_SERVICES_SHA1 = "56:84:F3:37:E2:F3:DB:F3:AB:1E:F0:1A:A0:7E:13:BF:C8:9E:BB:A3"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.getSigningSha1(): String? = try {
    val pm = packageManager
    val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
    }
    val raw = certs?.firstOrNull()?.toByteArray() ?: return null
    val digest = MessageDigest.getInstance("SHA-1").digest(raw)
    digest.joinToString(":") { "%02X".format(it) }
} catch (_: Exception) {
    null
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    trySend(currentUser)
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val activityContext = context.findActivity() ?: context

    scope.launch {
        try {
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val signedInUser = authResult.user ?: Firebase.auth.currentUser
                if (signedInUser != null && signedInUser.photoUrl == null && googleCred.profilePictureUri != null) {
                    runCatching {
                        signedInUser.updateProfile(
                            userProfileChangeRequest { photoUri = googleCred.profilePictureUri }
                        ).await()
                        signedInUser.reload().await()
                    }
                }
                withContext(Dispatchers.Main) { onAuthSuccess() }
            } else {
                withContext(Dispatchers.Main) { onUnauthenticated() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto sign-in skipped or unavailable: ${e.message}")
            withContext(Dispatchers.Main) { onUnauthenticated() }
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    val activityContext = context.findActivity() ?: context

    scope.launch {
        try {
            val result = try {
                credentialManager.getCredential(activityContext, request)
            } catch (primaryNoCred: NoCredentialException) {
                Log.w(TAG, "Primary SignInWithGoogle returned NoCredential, trying GetGoogleIdOption fallback", primaryNoCred)
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                credentialManager.getCredential(activityContext, fallbackRequest)
            } catch (primaryErr: GetCredentialException) {
                if (primaryErr is GetCredentialCancellationException) throw primaryErr
                Log.w(TAG, "Primary SignInWithGoogle failed (${primaryErr.javaClass.simpleName}), trying GetGoogleIdOption fallback", primaryErr)
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                credentialManager.getCredential(activityContext, fallbackRequest)
            }

            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                val authResult = withContext(NonCancellable) {
                    val res = Firebase.auth.signInWithCredential(authCredential).await()
                    val signedInUser = res.user ?: Firebase.auth.currentUser
                    if (signedInUser != null && signedInUser.photoUrl == null && googleCred.profilePictureUri != null) {
                        runCatching {
                            signedInUser.updateProfile(
                                userProfileChangeRequest { photoUri = googleCred.profilePictureUri }
                            ).await()
                            signedInUser.reload().await()
                        }
                    }
                    res
                }
                if (authResult.user != null || Firebase.auth.currentUser != null) {
                    withContext(Dispatchers.Main) { onAuthSuccess() }
                } else {
                    withContext(Dispatchers.Main) {
                        onAuthError("Firebase sign-in returned no user session. Please try again.")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onAuthError("Unexpected credential type returned.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val runtimeSha1 = context.getSigningSha1()
            val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
            if (runtimeSha1 != null && !runtimeSha1.equals(EXPECTED_GOOGLE_SERVICES_SHA1, ignoreCase = true)) {
                withContext(Dispatchers.Main) {
                    onAuthError(
                        "Google Sign-In was rejected or cancelled because this APK's SHA-1 ($runtimeSha1) does not match Firebase ($EXPECTED_GOOGLE_SERVICES_SHA1). Add $runtimeSha1 in Firebase Console -> Project Settings -> ${context.packageName}, OR set DEBUG_KEYSTORE_BASE64 in GitHub Secrets and rebuild."
                    )
                }
            } else if (detail != null) {
                withContext(Dispatchers.Main) { onAuthError("Google Sign-In cancelled: $detail") }
            } else {
                withContext(Dispatchers.Main) { onAuthCancelled() }
            }
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credentials available on device: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onAuthError("No Google account found on this device. Add a Google account in device settings and try again.")
            }
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error during Google Sign-In", e)
            val rawMsg = e.errorMessage?.toString() ?: e.localizedMessage ?: "Google Sign-In could not complete."
            val lower = rawMsg.lowercase()
            val runtimeSha1 = context.getSigningSha1()
            val sha1Mismatch = runtimeSha1 != null && !runtimeSha1.equals(EXPECTED_GOOGLE_SERVICES_SHA1, ignoreCase = true)
            val userMsg = if (
                sha1Mismatch ||
                "28444" in lower || "developer" in lower || "10:" in lower ||
                "16:" in lower || "reauth" in lower || "fingerprint" in lower || "caller" in lower
            ) {
                "Google Sign-In blocked: APK SHA-1 (${runtimeSha1 ?: "unknown"}) is not registered in Firebase Console for ${context.packageName}. Add this SHA-1 in Firebase Project Settings or sign with DEBUG_KEYSTORE_BASE64. ($rawMsg)"
            } else {
                rawMsg
            }
            withContext(Dispatchers.Main) { onAuthError(userMsg) }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            withContext(Dispatchers.Main) {
                onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
            }
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onBeforeSignOut: suspend () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            runCatching { onBeforeSignOut() }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
