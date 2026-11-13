package com.arenacards.feature.profile

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
class PlayerProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakePlayerProfileRepository
    private lateinit var viewModel: PlayerProfileViewModel
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePlayerProfileRepository()
        store = ViewModelStore()
        viewModel = ViewModelProvider(
            store,
            PlayerProfileViewModelFactory(repository)
        )[PlayerProfileViewModel::class.java]
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun profileCreationStoresTheTrustedRepositoryResult() {
        viewModel.createProfile("arena_one", "Arena One", "ember")

        assertEquals(1, repository.createCount)
        assertEquals("arena_one", repository.lastUsername)
        assertEquals(profile, viewModel.uiState.value.profile)
        assertEquals("Your player profile has been saved.", viewModel.uiState.value.message)
    }

    @Test
    fun invalidProfileUpdateDoesNotCallRepository() {
        viewModel.updateProfile("ab", "Player", "ember", ProfileVisibility.PUBLIC)

        assertEquals(0, repository.updateCount)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun profileEditsPassVisibilityAndUsernameToRepository() {
        viewModel.updateProfile("new_name", "New Name", "tide", ProfileVisibility.PUBLIC)

        assertEquals(1, repository.updateCount)
        assertEquals("new_name", repository.lastUsername)
        assertEquals(ProfileVisibility.PUBLIC, repository.lastVisibility)
        assertEquals("new_name", viewModel.uiState.value.profile?.username)
        assertEquals(ProfileVisibility.PUBLIC, viewModel.uiState.value.profile?.profileVisibility)
    }

    @Test
    fun repositoryFailureIsReportedWithoutSuccessState() {
        repository.createFailure = IllegalStateException("Profile service unavailable.")

        viewModel.createProfile("arena_one", "Arena One", "ember")

        assertEquals("Profile service unavailable.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun usernameAvailabilityErrorsAreVisible() {
        repository.usernameFailure = IllegalStateException("Username check failed.")

        viewModel.checkUsername("arena_one")

        assertEquals("Username check failed.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isCheckingUsername)
    }

    @Test
    fun profileLoadFailureIsNotTreatedAsAMissingProfile() {
        repository.profileFailure = IllegalStateException("Profile read denied.")

        viewModel.loadProfile("firebase-uid")

        assertTrue(viewModel.uiState.value.profileLoadFailed)
        assertEquals("Profile read denied.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.profile)
    }

    private class FakePlayerProfileRepository : PlayerProfileRepository {
        private val profileFlow = MutableStateFlow<PlayerProfile?>(null)
        var createCount = 0
        var updateCount = 0
        var lastUsername: String? = null
        var lastVisibility: ProfileVisibility? = null
        var createFailure: Exception? = null
        var usernameFailure: Exception? = null
        var profileFailure: Exception? = null

        override fun observeMyProfile(uid: String): Flow<PlayerProfile?> =
            profileFailure?.let { failure -> flow { throw failure } } ?: profileFlow

        override suspend fun isUsernameAvailable(username: String): Boolean {
            usernameFailure?.let { throw it }
            return true
        }

        override suspend fun createProfile(
            username: String,
            displayName: String,
            avatarId: String
        ): PlayerProfile {
            createCount++
            lastUsername = username
            createFailure?.let { throw it }
            return profile.also { profileFlow.value = it }
        }

        override suspend fun updateProfile(
            username: String,
            displayName: String,
            avatarId: String,
            visibility: ProfileVisibility
        ): PlayerProfile {
            updateCount++
            lastUsername = username
            lastVisibility = visibility
            return profile.copy(
                username = username,
                usernameNormalized = username.lowercase(),
                profileVisibility = visibility
            ).also { profileFlow.value = it }
        }

        override suspend fun searchPublicPlayers(usernamePrefix: String): List<PublicPlayer> = emptyList()
    }

    private companion object {
        val profile = PlayerProfile(
            uid = "firebase-uid",
            gamerId = "AR-827491",
            username = "arena_one",
            usernameNormalized = "arena_one",
            displayName = "Arena One",
            avatarId = "ember",
            createdAtMillis = 10L,
            updatedAtMillis = 20L,
            accountStatus = "active",
            profileVisibility = ProfileVisibility.PRIVATE,
            totalMatches = 0,
            wins = 0,
            losses = 0,
            draws = 0,
            experiencePoints = 0,
            playerLevel = 1
        )
    }
}
