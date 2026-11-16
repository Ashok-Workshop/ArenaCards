package com.arenacards.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arenacards.feature.friends.SocialNotification
import com.arenacards.feature.friends.SocialNotificationPreferences
import com.arenacards.feature.friends.SocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<SocialNotification> = emptyList(),
    val preferences: SocialNotificationPreferences = SocialNotificationPreferences(),
    val nextCursor: String? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null
)

class NotificationsViewModel(private val repository: SocialRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = runRequest {
        val notifications = repository.listNotifications()
        val preferences = repository.getNotificationPreferences()
        _uiState.value = _uiState.value.copy(
            notifications = notifications.items,
            preferences = preferences,
            nextCursor = notifications.nextCursor
        )
    }

    fun markRead(notification: SocialNotification) = runRequest {
        if (!notification.isRead) repository.markNotificationRead(notification.notificationId)
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.map {
                if (it.notificationId == notification.notificationId) it.copy(isRead = true) else it
            }
        )
    }

    fun loadMore() = runRequest {
        val cursor = _uiState.value.nextCursor ?: return@runRequest
        val page = repository.listNotifications(cursor)
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications + page.items,
            nextCursor = page.nextCursor
        )
    }

    fun setPreference(key: String, enabled: Boolean) = runRequest {
        val current = _uiState.value.preferences
        val updated = when (key) {
            "friendRequests" -> current.copy(friendRequests = enabled)
            "roomInvitations" -> current.copy(roomInvitations = enabled)
            "messages" -> current.copy(messages = enabled)
            "matchUpdates" -> current.copy(matchUpdates = enabled)
            "socialUpdates" -> current.copy(socialUpdates = enabled)
            else -> throw IllegalArgumentException("Unknown notification preference.")
        }
        repository.setNotificationPreferences(updated)
        _uiState.value = _uiState.value.copy(
            preferences = updated,
            noticeMessage = "Notification preferences saved."
        )
    }

    private fun runRequest(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Notifications could not be updated."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isBusy = false)
            }
        }
    }
}

class NotificationsViewModelFactory(
    private val repository: SocialRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NotificationsViewModel::class.java))
        return NotificationsViewModel(repository) as T
    }
}
