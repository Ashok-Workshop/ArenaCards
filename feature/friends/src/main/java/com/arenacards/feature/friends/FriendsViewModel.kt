package com.arenacards.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendsUiState(
    val gamerId: String = "",
    val searchResult: SocialPlayer? = null,
    val friends: List<SocialPlayer> = emptyList(),
    val incomingRequests: List<FriendRequest> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val blockedPlayers: List<SocialPlayer> = emptyList(),
    val invitations: List<RoomInvitation> = emptyList(),
    val friendsCursor: String? = null,
    val incomingCursor: String? = null,
    val outgoingCursor: String? = null,
    val blockedCursor: String? = null,
    val invitationsCursor: String? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null
)

class FriendsViewModel(private val repository: SocialRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun updateGamerId(value: String) {
        _uiState.value = _uiState.value.copy(
            gamerId = value.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(9),
            searchResult = null,
            errorMessage = null
        )
    }

    fun searchPlayer() = runRequest {
        val result = repository.searchPlayer(_uiState.value.gamerId.trim())
        _uiState.value = _uiState.value.copy(
            searchResult = result,
            noticeMessage = if (result == null) "No public player matched that Gamer ID." else null
        )
    }

    fun sendFriendRequest() = runRequest {
        val gamerId = _uiState.value.searchResult?.gamerId
            ?: throw IllegalStateException("Search for a player before sending a request.")
        repository.sendFriendRequest(gamerId, UUID.randomUUID().toString())
        _uiState.value = _uiState.value.copy(
            noticeMessage = "Friend request sent.",
            searchResult = null,
            gamerId = ""
        )
        refreshData()
    }

    fun acceptRequest(request: FriendRequest) = runRequest {
        repository.acceptFriendRequest(request)
        _uiState.value = _uiState.value.copy(noticeMessage = "Friend request accepted.")
        refreshData()
    }

    fun rejectRequest(request: FriendRequest) = runRequest {
        repository.rejectFriendRequest(request)
        refreshData()
    }

    fun cancelRequest(request: FriendRequest) = runRequest {
        repository.cancelFriendRequest(request)
        refreshData()
    }

    fun removeFriend(player: SocialPlayer) = runRequest {
        repository.removeFriend(player.uid)
        refreshData()
    }

    fun blockPlayer(player: SocialPlayer) = runRequest {
        repository.blockPlayer(player.uid)
        refreshData()
    }

    fun unblockPlayer(player: SocialPlayer) = runRequest {
        repository.unblockPlayer(player.uid)
        refreshData()
    }

    fun acceptInvitation(invitation: RoomInvitation, onAccepted: () -> Unit) = runRequest {
        repository.acceptRoomInvitation(invitation.invitationId)
        _uiState.value = _uiState.value.copy(
            noticeMessage = "Invitation accepted. Opening your private room."
        )
        refreshData()
        onAccepted()
    }

    fun rejectInvitation(invitation: RoomInvitation) = runRequest {
        repository.rejectRoomInvitation(invitation.invitationId)
        refreshData()
    }

    fun refresh() = runRequest { refreshData() }

    fun loadMoreFriends() = runRequest {
        val cursor = _uiState.value.friendsCursor ?: return@runRequest
        val page = repository.listFriends(cursor)
        _uiState.value = _uiState.value.copy(
            friends = _uiState.value.friends + page.items,
            friendsCursor = page.nextCursor
        )
    }

    fun loadMoreIncomingRequests() = runRequest {
        val cursor = _uiState.value.incomingCursor ?: return@runRequest
        val page = repository.listFriendRequests("incoming", cursor)
        _uiState.value = _uiState.value.copy(
            incomingRequests = _uiState.value.incomingRequests + page.items,
            incomingCursor = page.nextCursor
        )
    }

    fun loadMoreOutgoingRequests() = runRequest {
        val cursor = _uiState.value.outgoingCursor ?: return@runRequest
        val page = repository.listFriendRequests("outgoing", cursor)
        _uiState.value = _uiState.value.copy(
            outgoingRequests = _uiState.value.outgoingRequests + page.items,
            outgoingCursor = page.nextCursor
        )
    }

    fun loadMoreBlockedPlayers() = runRequest {
        val cursor = _uiState.value.blockedCursor ?: return@runRequest
        val page = repository.listBlockedPlayers(cursor)
        _uiState.value = _uiState.value.copy(
            blockedPlayers = _uiState.value.blockedPlayers + page.items,
            blockedCursor = page.nextCursor
        )
    }

    fun loadMoreInvitations() = runRequest {
        val cursor = _uiState.value.invitationsCursor ?: return@runRequest
        val page = repository.listRoomInvitations(cursor)
        _uiState.value = _uiState.value.copy(
            invitations = _uiState.value.invitations + page.items,
            invitationsCursor = page.nextCursor
        )
    }

    private suspend fun refreshData() {
        val friends = repository.listFriends()
        val incoming = repository.listFriendRequests("incoming")
        val outgoing = repository.listFriendRequests("outgoing")
        val blocked = repository.listBlockedPlayers()
        val invitations = repository.listRoomInvitations()
        _uiState.value = _uiState.value.copy(
            friends = friends.items,
            incomingRequests = incoming.items,
            outgoingRequests = outgoing.items,
            blockedPlayers = blocked.items,
            invitations = invitations.items,
            friendsCursor = friends.nextCursor,
            incomingCursor = incoming.nextCursor,
            outgoingCursor = outgoing.nextCursor,
            blockedCursor = blocked.nextCursor,
            invitationsCursor = invitations.nextCursor
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
                    errorMessage = error.message ?: "Social information could not be loaded."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isBusy = false)
            }
        }
    }
}

class FriendsViewModelFactory(
    private val repository: SocialRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FriendsViewModel::class.java))
        return FriendsViewModel(repository) as T
    }
}
