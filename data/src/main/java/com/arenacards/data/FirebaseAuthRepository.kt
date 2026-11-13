package com.arenacards.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.arenacards.feature.auth.AuthOperationException
import com.arenacards.feature.auth.AuthRepository
import com.arenacards.feature.auth.AuthUser
import com.arenacards.feature.auth.PhoneCodeResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    context: Context
) : AuthRepository {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context.applicationContext)
    private var phoneResendToken: PhoneAuthProvider.ForceResendingToken? = null

    override val currentUser: AuthUser?
        get() = firebaseAuth.currentUser?.toAuthUser()

    override fun observeAuthState(): Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun register(email: String, username: String, password: String): AuthUser =
        try {
            val user = firebaseAuth.createUserWithEmailAndPassword(email, password)
                .await()
                .user
                ?: throw AuthOperationException("Account creation completed without a user. Please try again.")
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(username).build()
            ).await()
            user.toAuthUser()
        } catch (error: FirebaseException) {
            throw error.toUserFacingError()
        }

    override suspend fun signIn(email: String, password: String): AuthUser =
        try {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .await()
                .user
                ?.toAuthUser()
                ?: throw AuthOperationException("Sign-in completed without a user. Please try again.")
        } catch (error: FirebaseException) {
            throw error.toUserFacingError()
        }

    override suspend fun sendPasswordReset(email: String) {
        try {
            firebaseAuth.sendPasswordResetEmail(email).await()
        } catch (error: FirebaseException) {
            throw error.toUserFacingError()
        }
    }

    override suspend fun sendEmailVerification() {
        val user = firebaseAuth.currentUser
            ?: throw AuthOperationException("Sign in to request an email verification link.")
        try {
            user.sendEmailVerification().await()
        } catch (error: FirebaseException) {
            throw error.toUserFacingError()
        }
    }

    override suspend fun reloadCurrentUser(): AuthUser? {
        val user = firebaseAuth.currentUser ?: return null
        try {
            user.reload().await()
        } catch (error: FirebaseException) {
            throw error.toUserFacingError()
        }
        return firebaseAuth.currentUser?.toAuthUser()
    }

    override suspend fun signInWithGoogle(activity: Activity, webClientId: String): AuthUser {
        val firebaseCredential = googleCredential(activity, webClientId)
        return try {
            firebaseAuth.signInWithCredential(firebaseCredential)
                .await()
                .user
                ?.toAuthUser()
                ?: throw AuthOperationException("Google sign-in completed without a user. Please try again.")
        } catch (error: FirebaseException) {
            throw error.toUserFacingError()
        }
    }

    override suspend fun linkGoogleToCurrentUser(
        activity: Activity,
        webClientId: String
    ): AuthUser {
        val user = firebaseAuth.currentUser
            ?: throw AuthOperationException("Sign in before linking another provider.")
        if (user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
            throw AuthOperationException("A Google account is already linked to this account.")
        }
        return try {
            user.linkWithCredential(googleCredential(activity, webClientId))
                .await()
                .user
                ?.toAuthUser()
                ?: throw AuthOperationException("Google account linking completed without a user. Please try again.")
        } catch (error: FirebaseException) {
            throw error.toUserFacingError()
        }
    }

    private suspend fun googleCredential(activity: Activity, webClientId: String) = run {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = try {
            credentialManager.getCredential(activity, request)
        } catch (_: GetCredentialCancellationException) {
            throw kotlinx.coroutines.CancellationException("Google sign-in was cancelled.")
        } catch (error: GetCredentialException) {
            throw AuthOperationException(
                "Google sign-in could not complete. Check the Google provider setup and try again.",
                error
            )
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw AuthOperationException("Google sign-in returned an unsupported credential.")
        }

        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (error: GoogleIdTokenParsingException) {
            throw AuthOperationException("Google sign-in returned an invalid credential.", error)
        }

        GoogleAuthProvider.getCredential(idToken, null)
    }

    override suspend fun sendPhoneCode(
        activity: Activity,
        phoneNumber: String,
        resend: Boolean
    ): PhoneCodeResult = suspendCancellableCoroutine { continuation ->
        if (hasUnverifiedEmailSession()) {
            continuation.resumeWithException(
                AuthOperationException("Verify your email or sign out before using phone sign-in.")
            )
            return@suspendCancellableCoroutine
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                val task = try {
                    phoneAuthTask(credential)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                    return
                }
                task
                    .addOnSuccessListener { result ->
                        val user = result.user?.toAuthUser()
                        if (user != null && continuation.isActive) {
                            continuation.resume(PhoneCodeResult.Authenticated(user))
                        }
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(error.toPhoneAuthError())
                        }
                    }
            }

            override fun onVerificationFailed(error: FirebaseException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(error.toPhoneAuthError())
                }
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                phoneResendToken = token
                if (continuation.isActive) {
                    continuation.resume(PhoneCodeResult.Sent(verificationId))
                }
            }

            override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                if (continuation.isActive) {
                    continuation.resume(PhoneCodeResult.Sent(verificationId))
                }
            }
        }

        try {
            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .apply {
                    if (resend) phoneResendToken?.let(::setForceResendingToken)
                }
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (error: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(error.toPhoneAuthError())
            }
        }
    }

    override suspend fun verifyPhoneCode(verificationId: String, code: String): AuthUser =
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            phoneAuthTask(credential).await().user?.toAuthUser()
                ?: throw AuthOperationException("Phone verification completed without an account. Please try again.")
        } catch (error: FirebaseException) {
            throw error.toPhoneAuthError()
        }

    override fun signOut() {
        firebaseAuth.signOut()
        phoneResendToken = null
    }

    private fun phoneAuthTask(credential: PhoneAuthCredential) =
        firebaseAuth.currentUser?.let { user ->
            if (user.providerData.any { it.providerId == "password" } && !user.isEmailVerified) {
                throw AuthOperationException("Verify your email or sign out before linking a phone number.")
            }
            user.linkWithCredential(credential)
        } ?: firebaseAuth.signInWithCredential(credential)

    private fun hasUnverifiedEmailSession(): Boolean =
        firebaseAuth.currentUser?.let { user ->
            user.providerData.any { it.providerId == "password" } && !user.isEmailVerified
        } == true

    private fun Exception.toPhoneAuthError(): AuthOperationException {
        val message = when ((this as? FirebaseAuthException)?.errorCode) {
            "ERROR_INVALID_PHONE_NUMBER" -> "Enter a valid phone number with country code."
            "ERROR_INVALID_VERIFICATION_CODE" -> "That verification code is incorrect. Check it and try again."
            "ERROR_SESSION_EXPIRED" -> "That verification code expired. Request a new one."
            "ERROR_CREDENTIAL_ALREADY_IN_USE",
            "ERROR_PHONE_NUMBER_ALREADY_EXISTS" ->
                "This phone number is linked to another account. Sign in with that account to recover access; it was not linked here."
            "ERROR_PROVIDER_ALREADY_LINKED" -> "A phone number is already linked to this account."
            "ERROR_QUOTA_EXCEEDED",
            "ERROR_TOO_MANY_REQUESTS" -> "SMS verification is temporarily limited. Wait and try again."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network connection failed. Check your connection and try again."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Phone sign-in is disabled in Firebase Authentication."
            else -> "Phone verification could not complete. Check the number and try again."
        }
        return AuthOperationException(message, this)
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified,
        providerIds = providerData.mapNotNull { it.providerId }.toSet()
    )

    private fun FirebaseException.toUserFacingError(): AuthOperationException {
        val message = when ((this as? FirebaseAuthException)?.errorCode) {
            "ERROR_INVALID_EMAIL" -> "That email address is not valid."
            "ERROR_WEAK_PASSWORD" -> "Choose a stronger password with at least 6 characters."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already uses that email. Try signing in instead."
            "ERROR_USER_NOT_FOUND",
            "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL" -> "The email or password is incorrect."
            "ERROR_USER_DISABLED" -> "This account is disabled. Contact support for help."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a little while, then try again."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network connection failed. Check your connection and try again."
            "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method is disabled in Firebase Authentication."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "An account already exists with a different sign-in method. Sign in with the original method first; no duplicate account was created."
            "ERROR_CREDENTIAL_ALREADY_IN_USE" ->
                "This Google credential belongs to another account. Sign in with that account's existing method to recover access; no accounts were merged."
            "ERROR_PROVIDER_ALREADY_LINKED" -> "This sign-in method is already linked to the account."
            else -> "Firebase Authentication could not complete the request. Check the Firebase Console setup and try again."
        }
        return AuthOperationException(message, this)
    }
}
