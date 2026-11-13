package com.arenacards.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeAuthRepository()
        viewModelStore = ViewModelStore()
        viewModel = ViewModelProvider(
            viewModelStore,
            AuthViewModelFactory(repository)
        )[AuthViewModel::class.java]
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun registrationSendsVerificationAndDoesNotTreatUnverifiedUserAsAuthenticated() {
        viewModel.register("player@example.com", "player_1", "strong-pass-1", "strong-pass-1")

        assertEquals(1, repository.registrationCount)
        assertEquals("player_1", repository.registrationUsername)
        assertEquals(1, repository.verificationEmailCount)
        assertTrue(viewModel.uiState.value.needsEmailVerification)
        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("verification link"))
    }

    @Test
    fun verifiedEmailSignInSetsAuthenticatedState() {
        repository.signInUser = verifiedUser

        viewModel.signIn("player@example.com", "strong-pass-1")

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertEquals(verifiedUser, viewModel.uiState.value.user)
    }

    @Test
    fun invalidRegistrationInputDoesNotCallRepository() {
        viewModel.register("not-an-email", "x", "short", "different")

        assertEquals(0, repository.registrationCount)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun mismatchedRegistrationPasswordsDoNotCallRepository() {
        viewModel.register("player@example.com", "player_1", "strong-pass-1", "strong-pass-2")

        assertEquals(0, repository.registrationCount)
        assertEquals("Passwords do not match.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun passwordResetUsesNonEnumeratingConfirmationMessage() {
        viewModel.sendPasswordReset("player@example.com")

        assertEquals("player@example.com", repository.passwordResetEmail)
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("If an account exists"))
    }

    @Test
    fun repositoryFailureIsPresentedAsAnAuthenticationError() {
        repository.passwordResetFailure = IllegalStateException("Network connection failed.")

        viewModel.sendPasswordReset("player@example.com")

        assertEquals("Network connection failed.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun signOutClearsPersistentAuthState() {
        repository.signInUser = verifiedUser
        viewModel.signIn("player@example.com", "strong-pass-1")

        viewModel.signOut()

        assertNull(viewModel.uiState.value.user)
        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertEquals(1, repository.signOutCount)
    }

    @Test
    fun phoneProviderIsAuthenticatedWithoutEmailVerification() {
        val phoneUser = AuthUser(
            uid = "phone-user",
            email = null,
            displayName = null,
            isEmailVerified = false,
            providerIds = setOf("phone")
        )

        assertEquals(
            AuthenticationStatus.PHONE_AUTHENTICATED,
            AuthUiState(user = phoneUser).authenticationStatus
        )
    }

    @Test
    fun authenticationStatusDistinguishesGoogleAndLinkedProviders() {
        val googleUser = verifiedUser.copy(
            isEmailVerified = false,
            providerIds = setOf("google.com")
        )
        val linkedUser = googleUser.copy(providerIds = setOf("password", "google.com"))

        assertEquals(
            AuthenticationStatus.GOOGLE_AUTHENTICATED,
            AuthUiState(user = googleUser).authenticationStatus
        )
        assertEquals(
            AuthenticationStatus.LINKED_PROVIDERS,
            AuthUiState(user = linkedUser).authenticationStatus
        )
    }

    @Test
    fun emailPasswordProviderRequiresVerification() {
        val pendingUser = AuthUser(
            uid = "pending-user",
            email = "player@example.com",
            displayName = "Player",
            isEmailVerified = false,
            providerIds = setOf("password")
        )

        assertEquals(
            AuthenticationStatus.EMAIL_PENDING_VERIFICATION,
            AuthUiState(user = pendingUser).authenticationStatus
        )
    }

    @Test
    fun authenticationStatusExposesLoadingAndErrors() {
        assertEquals(
            AuthenticationStatus.LOADING,
            AuthUiState(isLoading = true).authenticationStatus
        )
        assertEquals(
            AuthenticationStatus.AUTHENTICATION_ERROR,
            AuthUiState(errorMessage = "Try again").authenticationStatus
        )
    }

    private class FakeAuthRepository : AuthRepository {
        private val authState = MutableStateFlow<AuthUser?>(null)
        override val currentUser: AuthUser?
            get() = authState.value
        override fun observeAuthState(): Flow<AuthUser?> = authState
        var signInUser = verifiedUser
        var registrationCount = 0
        var verificationEmailCount = 0
        var signOutCount = 0
        var passwordResetEmail: String? = null
        var registrationUsername: String? = null
        var passwordResetFailure: Exception? = null

        override suspend fun register(email: String, username: String, password: String): AuthUser {
            registrationCount += 1
            registrationUsername = username
            return AuthUser(
                "new-user",
                email,
                username,
                false,
                setOf("password")
            ).also { authState.value = it }
        }

        override suspend fun signIn(email: String, password: String): AuthUser =
            signInUser.also { authState.value = it }

        override suspend fun sendPasswordReset(email: String) {
            passwordResetFailure?.let { throw it }
            passwordResetEmail = email
        }

        override suspend fun sendEmailVerification() {
            verificationEmailCount += 1
        }

        override suspend fun reloadCurrentUser(): AuthUser? = authState.value

        override suspend fun signInWithGoogle(activity: Activity, webClientId: String): AuthUser =
            verifiedUser.also { authState.value = it }

        override suspend fun linkGoogleToCurrentUser(activity: Activity, webClientId: String): AuthUser =
            verifiedUser.copy(providerIds = setOf("password", "google.com"))

        override suspend fun sendPhoneCode(
            activity: Activity,
            phoneNumber: String,
            resend: Boolean
        ): PhoneCodeResult = PhoneCodeResult.Sent("test-verification")

        override suspend fun verifyPhoneCode(verificationId: String, code: String): AuthUser =
            AuthUser("phone-user", null, null, false, setOf("phone"))

        override fun signOut() {
            signOutCount += 1
            authState.value = null
        }
    }

    private companion object {
        val verifiedUser = AuthUser(
            uid = "verified-user",
            email = "player@example.com",
            displayName = "Player",
            isEmailVerified = true,
            providerIds = setOf("password")
        )
    }
}
