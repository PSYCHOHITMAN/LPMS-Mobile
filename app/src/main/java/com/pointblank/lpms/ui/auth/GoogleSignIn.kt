package com.pointblank.lpms.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

sealed interface GoogleTokenResult {
    data class Token(val idToken: String) : GoogleTokenResult
    data object Cancelled : GoogleTokenResult
    data class Error(val message: String) : GoogleTokenResult
}

/** Shows the Google account picker and returns a Google ID token for Firebase Auth (single sign-on). */
suspend fun requestGoogleIdToken(context: Context): GoogleTokenResult {
    // default_web_client_id is generated from google-services.json once Google sign-in is enabled in Firebase.
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) {
        return GoogleTokenResult.Error(
            "Google sign-in is not set up yet. Enable it in Firebase, add your SHA-1 key, then download google-services.json again."
        )
    }
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(context.getString(resId)).build())
        .build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleTokenResult.Error("Unexpected sign-in response")
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleTokenResult.Cancelled
    } catch (e: CancellationException) {
        throw e
    } catch (e: GetCredentialException) {
        GoogleTokenResult.Error(e.message ?: "Google sign-in failed")
    } catch (e: Exception) {
        GoogleTokenResult.Error(e.message ?: "Google sign-in failed")
    }
}
