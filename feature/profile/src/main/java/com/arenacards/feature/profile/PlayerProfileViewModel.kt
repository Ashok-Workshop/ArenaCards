package com.arenacards.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerProfileUiState(
    val uid: String? = null,
    val profile: PlayerProfile? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isCheckingUsername: Boolean = false,
    val usernameAvailable: Boolean? = null,
    val errorMessage: String? = null,
    val message: String? = null,
    val hasLoaded: Boolean = false,
    val profileLoadFailed: Boolean = false
)

class PlayerProfileViewModel(
    private val repository: PlayerProfileRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerProfileUiState())
    val uiState: StateFlow<PlayerProfileUiState> = _uiState.asStateFlow()
    private var profileJob: Job? = null

    fun loadProfile(uid: String) {
        profileJob?.cancel()
        _uiState.value = PlayerProfileUiState(uid = uid, isLoading = true)
        profileJob = viewModelScope.launch {
            repository.observeMyProfile(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoaded = true,
                            profileLoadFailed = true,
                            errorMessage = if (error is Exception) {
                                error.userFacingMessage()
                            } else {
                                "The player profile could not be loaded. Check your connection and try again."
                            }
                        )
                    }
                }
                .collect { profile ->
                    _uiState.update {
                        it.copy(
                            profile = profile,
                            isLoading = false,
                            hasLoaded = true,
                            profileLoadFailed = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    fun checkUsername(username: String) {
        ProfileInputValidation.usernameError(username)?.let {
            setError(it)
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isCheckingUsername = true, usernameAvailable = null, errorMessage = null)
            }
            try {
                val available = repository.isUsernameAvailable(username.trim())
                _uiState.update {
                    it.copy(
                        isCheckingUsername = false,
                        usernameAvailable = available,
                        message = if (available) "Username is available."
                        else "That username is already taken."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingUsername = false,
                        errorMessage = error.userFacingMessage()
                    )
                }
            }
        }
    }

    fun createProfile(username: String, displayName: String, avatarId: String) {
        val error = validateProfile(username, displayName, avatarId)
        if (error != null) {
            setError(error)
            return
        }
        save {
            repository.createProfile(username.trim(), displayName.trim(), avatarId)
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        avatarId: String,
        visibility: ProfileVisibility
    ) {
        val error = validateProfile(username, displayName, avatarId)
        if (error != null) {
            setError(error)
            return
        }
        save {
            repository.updateProfile(
                username.trim(),
                displayName.trim(),
                avatarId,
                visibility
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, message = null) }
    }

    private fun save(action: suspend () -> PlayerProfile) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isSaving = true, errorMessage = null, message = null, usernameAvailable = null)
            }
            try {
                val profile = action()
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isSaving = false,
                        message = "Your player profile has been saved."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = error.userFacingMessage())
                }
            }
        }
    }

    private fun validateProfile(username: String, displayName: String, avatarId: String): String? =
        ProfileInputValidation.usernameError(username)
            ?: ProfileInputValidation.displayNameError(displayName)
            ?: if (!ProfileInputValidation.isValidAvatarId(avatarId)) {
                "Choose one of the available avatars."
            } else {
                null
            }

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message, message = null) }
    }

    private fun Exception.userFacingMessage(): String =
        message?.takeIf(String::isNotBlank)
            ?: "The player profile could not be loaded. Check your connection and try again."
}

class PlayerProfileViewModelFactory(
    private val repository: PlayerProfileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PlayerProfileViewModel::class.java)) {
            "Unsupported ViewModel type: ${modelClass.name}"
        }
        return PlayerProfileViewModel(repository) as T
    }
}
