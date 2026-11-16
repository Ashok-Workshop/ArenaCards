package com.arenacards.feature.multiplayer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MultiplayerViewModelTest {
    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun matchmakingUpdatesStatusAndCanBeCancelled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(repository)

        viewModel.joinMatchmaking()
        advanceUntilIdle()

        assertEquals("QUEUED", viewModel.uiState.value.playerStatus.status)
        assertTrue(viewModel.uiState.value.noticeMessage!!.contains("real players"))

        viewModel.cancelMatchmaking()
        advanceUntilIdle()

        assertEquals("IDLE", viewModel.uiState.value.playerStatus.status)
        assertEquals(1, repository.cancelCount)
    }

    @Test
    fun roomCreationReadyAndLeaveUseRepositoryAndLiveRoomState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(repository)

        viewModel.createRoom()
        advanceUntilIdle()
        repository.status.value = OnlinePlayerStatus(status = "IN_ROOM", roomId = "room-1")
        advanceUntilIdle()

        assertEquals("ROOM123456", viewModel.uiState.value.room?.roomCode)
        viewModel.setReady(true)
        advanceUntilIdle()
        assertTrue(repository.ready)

        viewModel.leaveRoom()
        advanceUntilIdle()
        assertEquals(1, repository.leaveCount)
        assertEquals(null, viewModel.uiState.value.room)
    }

    @Test
    fun pendingActionDoesNotOptimisticallyChangeAuthoritativeMatch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(repository)
        repository.status.value = OnlinePlayerStatus(status = "IN_MATCH", matchId = "match-1")
        runCurrent()

        val original = viewModel.uiState.value.match
        assertNotNull(original)
        viewModel.submitAction("PLAY_CARD", card = original!!.hand.first())
        runCurrent()

        assertTrue(viewModel.uiState.value.isSubmittingAction)
        assertEquals(original.stateVersion, viewModel.uiState.value.match?.stateVersion)
        assertEquals(original.topCard, viewModel.uiState.value.match?.topCard)

        repository.actionConfirmation.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSubmittingAction)
        assertEquals(original.stateVersion + 1, viewModel.uiState.value.pendingActionVersion)
        assertEquals(original.stateVersion, viewModel.uiState.value.match?.stateVersion)

        repository.status.value = OnlinePlayerStatus()
        runCurrent()
    }

    @Test
    fun unoCallAndCatchCanBeSubmittedOutsideTheCurrentTurn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(repository)
        repository.status.value = OnlinePlayerStatus(status = "IN_MATCH", matchId = "match-1")
        repository.actionConfirmation.complete(Unit)
        runCurrent()

        repository.match.value = repository.match.value.copy(
            currentPlayerUid = "player-2",
            pendingUnoPlayerUid = repository.currentUid,
            unoDeadlineMillis = 20_000
        )
        runCurrent()
        viewModel.submitAction("CALL_UNO")
        runCurrent()
        assertEquals(listOf("CALL_UNO"), repository.submittedActions)

        repository.match.value = repository.match.value.copy(
            currentPlayerUid = "player-2",
            pendingUnoPlayerUid = "player-2"
        )
        runCurrent()
        viewModel.submitAction("CATCH_UNO")
        runCurrent()
        assertEquals(listOf("CALL_UNO", "CATCH_UNO"), repository.submittedActions)

        repository.status.value = OnlinePlayerStatus()
        runCurrent()
    }

    @Test
    fun historyPagesAppendAndSurfaceBackendErrors() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(repository)

        viewModel.refreshHistory()
        advanceUntilIdle()
        assertEquals(listOf("match-1"), viewModel.uiState.value.history.map { it.matchId })
        assertTrue(viewModel.uiState.value.hasMoreHistory)

        viewModel.loadMoreHistory()
        advanceUntilIdle()
        assertEquals(
            listOf("match-1", "match-2"),
            viewModel.uiState.value.history.map { it.matchId }
        )
        assertFalse(viewModel.uiState.value.hasMoreHistory)

        repository.historyError = IllegalStateException("History unavailable.")
        viewModel.refreshHistory()
        advanceUntilIdle()
        assertEquals("History unavailable.", viewModel.uiState.value.errorMessage)
    }

    private class FakeMultiplayerRepository : MultiplayerRepository {
        override val currentUid = "player-1"
        val status = MutableStateFlow(OnlinePlayerStatus())
        val match = MutableStateFlow(activeMatch())
        val actionConfirmation = CompletableDeferred<Unit>()
        val submittedActions = mutableListOf<String>()
        var cancelCount = 0
        var leaveCount = 0
        var ready = false
        var historyError: Exception? = null

        override fun observePlayerStatus() = status
        override fun observeRoom(roomId: String) = MutableStateFlow(room())
        override fun observeMatch(matchId: String) = match

        override suspend fun joinMatchmaking(playerCount: Int, requestId: String): OnlinePlayerStatus {
            status.value = OnlinePlayerStatus(status = "QUEUED")
            return status.value
        }

        override suspend fun cancelMatchmaking() {
            cancelCount += 1
            status.value = OnlinePlayerStatus()
        }

        override suspend fun createPrivateRoom(
            playerCount: Int,
            requestId: String
        ) = room()

        override suspend fun joinPrivateRoom(roomCode: String, requestId: String) = room()

        override suspend fun setRoomReady(roomId: String, ready: Boolean, requestId: String) {
            this.ready = ready
        }

        override suspend fun leaveRoom(roomId: String, requestId: String) {
            leaveCount += 1
            status.value = OnlinePlayerStatus()
        }

        override suspend fun startRoom(roomId: String, requestId: String) = "match-1"

        override suspend fun reconnectToMatch(matchId: String) = Unit
        override suspend fun heartbeatMatch(matchId: String) = Unit

        override suspend fun submitAction(
            matchId: String,
            action: String,
            cardId: String?,
            chosenColor: String?,
            expectedTurnNumber: Int,
            expectedStateVersion: Int,
            requestId: String
        ) {
            submittedActions += action
            actionConfirmation.await()
        }

        override suspend fun processExpiredTurn(matchId: String, requestId: String) = false

        override suspend fun getHistory(
            pageSize: Int,
            cursor: OnlineMatchHistoryCursor?
        ): OnlineMatchHistoryPage {
            historyError?.let { throw it }
            return if (cursor == null) {
                OnlineMatchHistoryPage(
                    matches = listOf(history("match-1")),
                    nextCursor = OnlineMatchHistoryCursor(10, "match-1")
                )
            } else {
                OnlineMatchHistoryPage(matches = listOf(history("match-2")), nextCursor = null)
            }
        }

        private fun room() = OnlineRoomSnapshot(
            roomId = "room-1",
            roomCode = "ROOM123456",
            ownerUid = currentUid,
            memberUids = listOf(currentUid),
            players = listOf(
                OnlineRoomPlayer(currentUid, "Player", "player", "AR-123456", "default", true)
            ),
            capacity = 2,
            status = "WAITING",
            expiresAtMillis = 100_000,
            matchId = null
        )

        private fun activeMatch() = OnlineMatchSnapshot(
            matchId = "match-1",
            status = "ACTIVE",
            mode = "STANDARD",
            playerUids = listOf(currentUid, "player-2"),
            players = listOf(
                OnlinePlayer(currentUid, "Player", "player", "AR-123456", "default", 0, 1, 0, "ONLINE", null),
                OnlinePlayer("player-2", "Opponent", "opponent", "AR-654321", "default", 1, 1, 0, "ONLINE", null)
            ),
            currentPlayerUid = currentUid,
            turnNumber = 1,
            stateVersion = 1,
            direction = 1,
            activeColor = "RED",
            topCard = OnlineCard("top", "RED", 2, false),
            discardHistory = emptyList(),
            drawPileCount = 80,
            hasDrawnThisTurn = false,
            challengePending = false,
            challengeTargetUid = null,
            drawnCardId = null,
            turnDeadlineMillis = 60_000,
            createdAtMillis = 1,
            result = null,
            completionReason = null,
            hand = listOf(OnlineCard("hand-1", "RED", 3, false))
        )

        private fun history(id: String) = OnlineMatchHistoryEntry(
            matchId = id,
            status = "COMPLETED",
            verified = true,
            outcome = "WIN",
            winnerUid = currentUid,
            finalPosition = 1,
            durationMillis = 1_000,
            completionReason = "LAST_CARD",
            completedAtMillis = 10,
            players = emptyList()
        )
    }
}
