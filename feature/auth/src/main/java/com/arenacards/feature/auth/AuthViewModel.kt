package com.arenacards.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthenticationStatus {
    UNAUTHENTICATED,
    EMAIL_PENDING_VERIFICATION,
    EMAIL_VERIFIED,
    GOOGLE_AUTHENTICATED,
    PHONE_AUTHENTICATED,
    LINKED_PROVIDERS,
    LOADING,
    AUTHENTICATION_ERROR
}

data class AuthUiState(
    val user: AuthUser? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val phoneVerificationId: String? = null,
    val phoneNumber: String? = null,
    val resendCooldownSeconds: Int = 0
) {
    val isAuthenticated: Boolean
        get() = user?.let {
            it.isEmailVerified || it.providerIds.any { provider ->
                provider == GOOGLE_PROVIDER || provider == PHONE_PROVIDER
            }
        } == true

    val needsEmailVerification: Boolean
        get() = user != null &&
            user.providerIds.contains(EMAIL_PROVIDER) &&
            !user.isEmailVerified &&
            !isAuthenticated

    val authenticationStatus: AuthenticationStatus
        get() = when {
            isLoading -> AuthenticationStatus.LOADING
            errorMessage != null -> AuthenticationStatus.AUTHENTICATION_ERROR
            needsEmailVerification -> AuthenticationStatus.EMAIL_PENDING_VERIFICATION
            user?.providerIds.orEmpty().count { it in setOf(EMAIL_PROVIDER, GOOGLE_PROVIDER, PHONE_PROVIDER) } > 1 ->
                AuthenticationStatus.LINKED_PROVIDERS
            user?.providerIds?.contains(PHONE_PROVIDER) == true ->
                AuthenticationStatus.PHONE_AUTHENTICATED
            user?.providerIds?.contains(GOOGLE_PROVIDER) == true ->
                AuthenticationStatus.GOOGLE_AUTHENTICATED
            isAuthenticated -> AuthenticationStatus.EMAIL_VERIFIED
            else -> AuthenticationStatus.UNAUTHENTICATED
        }

    private companion object {
        const val EMAIL_PROVIDER = "password"
        const val GOOGLE_PROVIDER = "google.com"
        const val PHONE_PROVIDER = "phone"
    }
}

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState(user = repository.currentUser))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAuthState().collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
    }

    fun register(email: String, username: String, password: String, confirmPassword: String) {
        val error = when {
            !AuthInputValidation.isValidEmail(email) -> "Enter a valid email address."
            !AuthInputValidation.isValidUsername(username) ->
                "Username must be 3–20 characters using letters, numbers, or underscores."
            AuthInputValidation.passwordError(password) != null ->
                AuthInputValidation.passwordError(password)
            password != confirmPassword -> "Passwords do not match."
            else -> null
        }
        if (error != null) {
            setError(error)
            return
        }
        perform {
            repository.register(email.trim(), username.trim(), password)
            repository.sendEmailVerification()
            _uiState.update {
                it.copy(
                    message = "Account created. Check your inbox for the verification link before signing in."
                )
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (!AuthInputValidation.isValidEmail(email) || password.isBlank()) {
            setError("Enter a valid email address and password.")
            return
        }
        perform {
            val user = repository.signIn(email.trim(), password)
            if (user.isEmailVerified) {
                _uiState.update { it.copy(message = "Signed in successfully.") }
            } else {
                repository.sendEmailVerification()
                _uiState.update {
                    it.copy(
                        message = "Your email is not verified yet. We sent another verification link."
                    )
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (!AuthInputValidation.isValidEmail(email)) {
            setError("Enter a valid email address.")
            return
        }
        perform {
            repository.sendPasswordReset(email.trim())
            _uiState.update {
                it.copy(message = "If an account exists for that email, reset instructions have been sent.")
            }
        }
    }

    fun resendVerification() {
        perform {
            repository.sendEmailVerification()
            _uiState.update {
                it.copy(message = "A new verification link has been sent.")
            }
        }
    }

    fun refreshVerificationStatus() {
        perform {
            val user = repository.reloadCurrentUser()
            _uiState.update {
                it.copy(
                    user = user,
                    message = if (user?.isEmailVerified == true) {
                        "Email verified. You can continue."
                    } else {
                        "Email is not verified yet. Open the link we sent and try again."
                    }
                )
            }
        }
    }

    fun signInWithGoogle(activity: Activity, webClientId: String?) {
        if (webClientId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Google sign-in is not configured. Enable the Google provider in Firebase Authentication and download the updated google-services.json.",
                    message = null
                )
            }
            return
        }
        perform {
            repository.signInWithGoogle(activity, webClientId)
            _uiState.update { it.copy(message = "Signed in with Google.") }
        }
    }

    fun linkGoogleToCurrentUser(activity: Activity, webClientId: String?) {
        if (webClientId.isNullOrBlank()) {
            setError("Google linking is unavailable until Firebase Google sign-in is configured.")
            return
        }
        if (!_uiState.value.isAuthenticated) {
            setError("Sign in and verify your account before linking Google.")
            return
        }
        perform {
            val user = repository.linkGoogleToCurrentUser(activity, webClientId)
            _uiState.update { it.copy(user = user, message = "Google account linked.") }
        }
    }

    fun signOut() {
        repository.signOut()
        _uiState.update {
            AuthUiState(message = "Signed out.")
        }
    }

    fun sendPhoneCode(activity: Activity, phoneNumber: String, resend: Boolean = false) {
        if (!AuthInputValidation.isValidPhoneNumber(phoneNumber)) {
            setError("Enter a valid phone number in international format.")
            return
        }
        if (resend && _uiState.value.resendCooldownSeconds > 0) return
        perform {
            when (val result = repository.sendPhoneCode(activity, phoneNumber, resend)) {
                is PhoneCodeResult.Sent -> {
                    _uiState.update {
                        it.copy(
                            phoneVerificationId = result.verificationId,
                            phoneNumber = phoneNumber,
                            resendCooldownSeconds = RESEND_COOLDOWN_SECONDS,
                            message = if (resend) "A new verification code was requested."
                            else "A verification code was sent."
                        )
                    }
                    startResendCountdown()
                }
                is PhoneCodeResult.Authenticated -> {
                    _uiState.update {
                        it.copy(
                            user = result.user,
                            phoneVerificationId = null,
                            phoneNumber = phoneNumber,
                            message = "Phone number verified."
                        )
                    }
                }
            }
        }
    }

    fun verifyPhoneCode(code: String) {
        val verificationId = _uiState.value.phoneVerificationId
        if (verificationId == null) {
            setError("Request a new verification code first.")
            return
        }
        if (!AuthInputValidation.isValidOtp(code)) {
            setError("Enter the 6-digit verification code.")
            return
        }
        perform {
            val wasAuthenticated = _uiState.value.isAuthenticated
            val user = repository.verifyPhoneCode(verificationId, code)
            _uiState.update {
                it.copy(
                    user = user,
                    phoneVerificationId = null,
                    message = if (wasAuthenticated) {
                        "Phone number verified and linked to your account."
                    } else {
                        "Signed in with your phone number."
                    }
                )
            }
        }
    }

    fun cancelPhoneVerification() {
        _uiState.update {
            it.copy(
                phoneVerificationId = null,
                phoneNumber = null,
                resendCooldownSeconds = 0,
                message = null,
                errorMessage = null
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, message = null) }
    }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = error.message
                            ?.takeIf { message -> message.isNotBlank() }
                            ?: "The request could not be completed. Check your connection and try again."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun setError(message: String) {
        _uiState.update {
            it.copy(errorMessage = message, message = null)
        }
    }

    private fun startResendCountdown() {
        viewModelScope.launch {
            while (_uiState.value.resendCooldownSeconds > 0) {
                kotlinx.coroutines.delay(1_000)
                _uiState.update {
                    it.copy(resendCooldownSeconds = (it.resendCooldownSeconds - 1).coerceAtLeast(0))
                }
            }
        }
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}

class AuthViewModelFactory(
    private val repository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            "Unsupported ViewModel type: ${modelClass.name}"
        }
        return AuthViewModel(repository) as T
    }
}
