package com.arenacards.feature.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MultiplayerFeedback {
    MATCH_FOUND,
    PLAYER_JOINED,
    YOUR_TURN,
    OPPONENT_PLAYED,
    CARD_DRAWN,
    PENALTY,
    MATCH_WON,
    MATCH_LOST
}

data class MultiplayerUiState(
    val playerCount: Int = 2,
    val playerStatus: OnlinePlayerStatus = OnlinePlayerStatus(),
    val room: OnlineRoomSnapshot? = null,
    val match: OnlineMatchSnapshot? = null,
    val isBusy: Boolean = false,
    val isSubmittingAction: Boolean = false,
    val pendingActionVersion: Int? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val feedbackSequence: Long = 0,
    val feedbackEvent: MultiplayerFeedback? = null,
    val history: List<OnlineMatchHistoryEntry> = emptyList(),
    val historyCursor: OnlineMatchHistoryCursor? = null,
    val isLoadingHistory: Boolean = false,
    val hasMoreHistory: Boolean = false
)

class MultiplayerViewModel(
    private val repository: MultiplayerRepository
) : ViewModel() {
    val currentUid: String
        get() = repository.currentUid

    private val _uiState = MutableStateFlow(MultiplayerUiState())
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    private var roomObservationId: String? = null
    private var matchObservationId: String? = null
    private var roomJob: Job? = null
    private var matchJob: Job? = null
    private var heartbeatJob: Job? = null
    private var previousMatch: OnlineMatchSnapshot? = null

    init {
        viewModelScope.launch {
            repository.observePlayerStatus()
                .catch { error ->
                    if (error is CancellationException) throw error
                    showError(error)
                }
                .collect { status ->
                    _uiState.value = _uiState.value.copy(playerStatus = status)
                    observeRoom(status.roomId.takeIf { status.status == "IN_ROOM" })
                    val matchId = status.matchId.takeIf { status.status == "IN_MATCH" }
                        ?: status.lastMatchId?.takeIf {
                            it == _uiState.value.match?.matchId
                        }
                    observeMatch(matchId)
                }
        }
    }

    fun selectPlayerCount(count: Int) {
        if (count in 2..4 && !_uiState.value.isBusy) {
            _uiState.value = _uiState.value.copy(playerCount = count, errorMessage = null)
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, noticeMessage = null)
    }

    fun joinMatchmaking() = runRequest {
        val result = repository.joinMatchmaking(_uiState.value.playerCount, requestId())
        _uiState.value = _uiState.value.copy(
            noticeMessage = if (result.matchId != null) {
                "A table is ready. Connecting securely…"
            } else {
                "Searching for real players. No CPU seats will be added."
            }
        )
    }

    fun cancelMatchmaking() = runRequest {
        repository.cancelMatchmaking()
        _uiState.value = _uiState.value.copy(noticeMessage = "Search cancelled.")
    }

    fun createRoom() = runRequest {
        val room = repository.createPrivateRoom(_uiState.value.playerCount, requestId())
        _uiState.value = _uiState.value.copy(room = room, noticeMessage = "Private room created.")
    }

    fun joinRoom(code: String) = runRequest {
        val room = repository.joinPrivateRoom(code, requestId())
        _uiState.value = _uiState.value.copy(room = room, noticeMessage = "Joined private room.")
    }

    fun setReady(ready: Boolean) = runRequest {
        val room = _uiState.value.room ?: return@runRequest
        repository.setRoomReady(room.roomId, ready, requestId())
    }

    fun updateRoomRules(turnDurationMillis: Long, allowWildDrawFourChallenge: Boolean) = runRequest {
        val room = _uiState.value.room ?: return@runRequest
        repository.updateRoomRules(
            room.roomId,
            turnDurationMillis,
            allowWildDrawFourChallenge,
            requestId()
        )
        _uiState.value = _uiState.value.copy(noticeMessage = "Private-room rules updated.")
    }

    fun leaveRoom() = runRequest {
        val room = _uiState.value.room ?: return@runRequest
        repository.leaveRoom(room.roomId, requestId())
        _uiState.value = _uiState.value.copy(room = null, noticeMessage = "You left the room.")
    }

    fun startRoom() = runRequest {
        val room = _uiState.value.room ?: return@runRequest
        val matchId = repository.startRoom(room.roomId, requestId())
        _uiState.value = _uiState.value.copy(
            playerStatus = _uiState.value.playerStatus.copy(status = "IN_MATCH", matchId = matchId),
            noticeMessage = "Match started. The server dealt the cards."
        )
        observeMatch(matchId)
    }

    fun submitAction(action: String, card: OnlineCard? = null, color: String? = null) {
        val state = _uiState.value
        val match = state.match ?: return
        if (state.isSubmittingAction || match.status != "ACTIVE") return
        val isUnoCall = action == "CALL_UNO" &&
            match.pendingUnoPlayerUid == repository.currentUid
        val isUnoCatch = action == "CATCH_UNO" &&
            match.pendingUnoPlayerUid != null &&
            match.pendingUnoPlayerUid != repository.currentUid
        if (!isUnoCall && !isUnoCatch && match.currentPlayerUid != repository.currentUid) return
        val expectedVersion = match.stateVersion
        _uiState.value = state.copy(
            isSubmittingAction = true,
            pendingActionVersion = expectedVersion + 1,
            errorMessage = null
        )
        viewModelScope.launch {
            try {
                repository.submitAction(
                    matchId = match.matchId,
                    action = action,
                    cardId = card?.id,
                    chosenColor = color,
                    expectedTurnNumber = match.turnNumber,
                    expectedStateVersion = expectedVersion,
                    requestId = requestId()
                )
                _uiState.value = _uiState.value.copy(
                    noticeMessage = "Action sent. Waiting for the server to confirm…"
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error)
                try {
                    repository.reconnectToMatch(match.matchId)
                } catch (refreshError: Exception) {
                    if (refreshError is CancellationException) throw refreshError
                    showError(refreshError)
                }
            } finally {
                _uiState.value = _uiState.value.copy(isSubmittingAction = false)
            }
        }
    }

    fun refreshHistory() {
        if (_uiState.value.isLoadingHistory) return
        _uiState.value = _uiState.value.copy(isLoadingHistory = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val page = repository.getHistory(HISTORY_PAGE_SIZE, null)
                _uiState.value = _uiState.value.copy(
                    history = page.matches,
                    historyCursor = page.nextCursor,
                    hasMoreHistory = page.nextCursor != null
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error)
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingHistory = false)
            }
        }
    }

    fun loadMoreHistory() {
        val cursor = _uiState.value.historyCursor ?: return
        if (_uiState.value.isLoadingHistory) return
        _uiState.value = _uiState.value.copy(isLoadingHistory = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val page = repository.getHistory(HISTORY_PAGE_SIZE, cursor)
                _uiState.value = _uiState.value.copy(
                    history = _uiState.value.history + page.matches,
                    historyCursor = page.nextCursor,
                    hasMoreHistory = page.nextCursor != null
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error)
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingHistory = false)
            }
        }
    }

    fun processExpiredTurn() {
        val match = _uiState.value.match ?: return
        if (match.status != "ACTIVE") return
        viewModelScope.launch {
            try {
                repository.processExpiredTurn(match.matchId, requestId())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error)
            }
        }
    }

    private fun observeRoom(roomId: String?) {
        if (roomId == roomObservationId) return
        roomObservationId = roomId
        roomJob?.cancel()
        if (roomId == null) {
            _uiState.value = _uiState.value.copy(room = null)
            return
        }
        roomJob = viewModelScope.launch {
            repository.observeRoom(roomId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    showError(error)
                }
                .collect { room ->
                    _uiState.value = _uiState.value.copy(room = room)
                    if (room == null) _uiState.value = _uiState.value.copy(
                        noticeMessage = "The private room is no longer available."
                    )
                }
        }
    }

    private fun observeMatch(matchId: String?) {
        if (matchId == matchObservationId) return
        matchObservationId = matchId
        matchJob?.cancel()
        heartbeatJob?.cancel()
        if (matchId == null) return
        matchJob = viewModelScope.launch {
            try {
                repository.reconnectToMatch(matchId)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error)
            }
            repository.observeMatch(matchId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    showError(error)
                }
                .collect { match ->
                    val old = previousMatch
                    previousMatch = match
                    _uiState.value = _uiState.value.copy(
                        match = match,
                        pendingActionVersion = _uiState.value.pendingActionVersion
                            ?.takeIf { match.stateVersion < it },
                        noticeMessage = match.result?.let { result ->
                            if (result.winnerUid == repository.currentUid) {
                                "Verified match complete."
                            } else {
                                "Match complete. Result verified by ArenaCards."
                            }
                        } ?: _uiState.value.noticeMessage,
                        feedbackSequence = _uiState.value.feedbackSequence + 1,
                        feedbackEvent = feedbackFor(old, match)
                    )
                }
        }
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                try {
                    repository.heartbeatMatch(matchId)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    showError(error)
                }
                delay(HEARTBEAT_INTERVAL_MILLIS)
            }
        }
    }

    private fun feedbackFor(
        previous: OnlineMatchSnapshot?,
        current: OnlineMatchSnapshot
    ): MultiplayerFeedback? {
        if (previous == null) return if (current.status == "ACTIVE") MultiplayerFeedback.MATCH_FOUND else null
        if (previous.status == "ACTIVE" && current.status != "ACTIVE") {
            return when (current.result?.winnerUid) {
                repository.currentUid -> MultiplayerFeedback.MATCH_WON
                null -> null
                else -> MultiplayerFeedback.MATCH_LOST
            }
        }
        if (previous.topCard.id != current.topCard.id) {
            return if (current.topCard.value >= 10) MultiplayerFeedback.PENALTY
            else MultiplayerFeedback.OPPONENT_PLAYED
        }
        if (previous.currentPlayerUid != current.currentPlayerUid
            && current.currentPlayerUid != null) {
            return MultiplayerFeedback.YOUR_TURN
        }
        return null
    }

    private fun runRequest(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error)
            } finally {
                _uiState.value = _uiState.value.copy(isBusy = false)
            }
        }
    }

    private fun showError(error: Throwable) {
        _uiState.value = _uiState.value.copy(
            errorMessage = error.message ?: "The online service could not complete the request."
        )
    }

    private fun requestId(): String = UUID.randomUUID().toString()

    override fun onCleared() {
        roomJob?.cancel()
        matchJob?.cancel()
        heartbeatJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val HISTORY_PAGE_SIZE = 20
        private const val HEARTBEAT_INTERVAL_MILLIS = 25_000L
    }
}

class MultiplayerViewModelFactory(
    private val repository: MultiplayerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MultiplayerViewModel::class.java) {
            "MultiplayerViewModelFactory only creates MultiplayerViewModel."
        }
        return MultiplayerViewModel(repository) as T
    }
}
