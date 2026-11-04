package com.runmoni.app.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.runmoni.app.BuildConfig

private val googlePickerGate = GooglePickerGate()

/** Interactive only. The returned credential is never stored, logged, or copied into UI state. */
internal suspend fun pickGoogleCredential(context: Context, onCredentialReceived: () -> Unit = {}): AuthCredential = googlePickerGate.select {
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.FIREBASE_WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = CredentialManager.create(context).getCredential(context, request)
    onCredentialReceived()
    val credential = result.credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
    val google = GoogleIdTokenCredential.createFrom(credential.data)
    GoogleAuthProvider.getCredential(google.idToken, null)
}
