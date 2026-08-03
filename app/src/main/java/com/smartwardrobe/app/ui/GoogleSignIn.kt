package com.smartwardrobe.app.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

suspend fun signInWithGoogle(
    context: Context,
    auth: FirebaseAuth,
    onComplete: (Result<FirebaseUser>) -> Unit
) {
    val clientIdRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (clientIdRes == 0) {
        onComplete(
            Result.failure(
                IllegalStateException("Google OAuth client is missing. Add SHA-1 in Firebase, re-download google-services.json, then sync again.")
            )
        )
        return
    }

    try {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(clientIdRes))
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnSuccessListener { result ->
                    result.user?.let { onComplete(Result.success(it)) }
                        ?: onComplete(Result.failure(IllegalStateException("Firebase did not return a user.")))
                }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } else {
            onComplete(Result.failure(IllegalStateException("Selected credential is not a Google ID token.")))
        }
    } catch (error: GetCredentialException) {
        onComplete(Result.failure(error))
    } catch (error: Exception) {
        onComplete(Result.failure(error))
    }
}
