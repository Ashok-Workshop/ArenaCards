package com.arenacards.feature.auth

import android.app.Activity
import kotlinx.coroutines.flow.Flow

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val providerIds: Set<String> = emptySet()
)

sealed interface PhoneCodeResult {
    data class Sent(val verificationId: String) : PhoneCodeResult
    data class Authenticated(val user: AuthUser) : PhoneCodeResult
}

class AuthOperationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

interface AuthRepository {
    val currentUser: AuthUser?

    fun observeAuthState(): Flow<AuthUser?>

    suspend fun register(email: String, username: String, password: String): AuthUser

    suspend fun signIn(email: String, password: String): AuthUser

    suspend fun sendPasswordReset(email: String)

    suspend fun sendEmailVerification()

    suspend fun reloadCurrentUser(): AuthUser?

    suspend fun signInWithGoogle(activity: Activity, webClientId: String): AuthUser

    suspend fun linkGoogleToCurrentUser(activity: Activity, webClientId: String): AuthUser

    suspend fun sendPhoneCode(
        activity: Activity,
        phoneNumber: String,
        resend: Boolean
    ): PhoneCodeResult

    suspend fun verifyPhoneCode(verificationId: String, code: String): AuthUser

    fun signOut()
}
