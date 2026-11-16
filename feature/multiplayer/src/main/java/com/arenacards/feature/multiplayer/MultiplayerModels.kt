package com.arenacards.feature.multiplayer

import kotlinx.coroutines.flow.Flow

data class OnlinePlayerStatus(
    val status: String = "IDLE",
    val roomId: String? = null,
    val matchId: String? = null,
    val lastMatchId: String? = null,
    val queueExpiresAtMillis: Long? = null
)

data class OnlineRoomPlayer(
    val uid: String,
    val displayName: String,
    val username: String,
    val gamerId: String,
    val avatarId: String,
    val ready: Boolean
)

data class OnlineRoomSnapshot(
    val roomId: String,
    val roomCode: String,
    val ownerUid: String,
    val memberUids: List<String>,
    val players: List<OnlineRoomPlayer>,
    val capacity: Int,
    val status: String,
    val expiresAtMillis: Long,
    val matchId: String?,
    val turnDurationMillis: Long = 45_000,
    val allowWildDrawFourChallenge: Boolean = true
)

data class OnlineCard(
    val id: String,
    val color: String,
    val value: Int,
    val isWild: Boolean
) {
    val displayName: String
        get() = when (value) {
            10 -> "Skip"
            11 -> "Reverse"
            12 -> "Draw Two"
            13 -> "Wild"
            14 -> "Wild Draw Four"
            else -> value.toString()
        }
}

data class OnlinePlayer(
    val uid: String,
    val displayName: String,
    val username: String,
    val gamerId: String,
    val avatarId: String,
    val seat: Int,
    val cardCount: Int,
    val timeoutCount: Int,
    val connectionState: String,
    val lastSeenAtMillis: Long?
)

data class OnlineMatchPosition(
    val uid: String,
    val position: Int,
    val remainingCards: Int
)

data class OnlineMatchResult(
    val verified: Boolean,
    val winnerUid: String?,
    val completedAtMillis: Long?,
    val durationMillis: Long,
    val completionReason: String,
    val positions: List<OnlineMatchPosition>
)

data class OnlineMatchSnapshot(
    val matchId: String,
    val status: String,
    val mode: String,
    val playerUids: List<String>,
    val players: List<OnlinePlayer>,
    val currentPlayerUid: String?,
    val turnNumber: Int,
    val stateVersion: Int,
    val direction: Int,
    val activeColor: String,
    val topCard: OnlineCard,
    val discardHistory: List<OnlineCard>,
    val drawPileCount: Int,
    val hasDrawnThisTurn: Boolean,
    val challengePending: Boolean,
    val challengeTargetUid: String?,
    val drawnCardId: String?,
    val turnDeadlineMillis: Long?,
    val createdAtMillis: Long,
    val result: OnlineMatchResult?,
    val completionReason: String?,
    val hand: List<OnlineCard>,
    val pendingUnoPlayerUid: String? = null,
    val unoDeadlineMillis: Long? = null
)

data class OnlineMatchHistoryEntry(
    val matchId: String,
    val status: String,
    val verified: Boolean,
    val outcome: String,
    val winnerUid: String?,
    val finalPosition: Int?,
    val durationMillis: Long,
    val completionReason: String,
    val completedAtMillis: Long?,
    val players: List<OnlinePlayer>
)

data class OnlineMatchHistoryCursor(
    val beforeCompletedAtMillis: Long,
    val beforeMatchId: String
)

data class OnlineMatchHistoryPage(
    val matches: List<OnlineMatchHistoryEntry>,
    val nextCursor: OnlineMatchHistoryCursor?
)

interface MultiplayerRepository {
    val currentUid: String
    fun observePlayerStatus(): Flow<OnlinePlayerStatus>
    fun observeRoom(roomId: String): Flow<OnlineRoomSnapshot?>
    fun observeMatch(matchId: String): Flow<OnlineMatchSnapshot>
    suspend fun joinMatchmaking(playerCount: Int, requestId: String): OnlinePlayerStatus
    suspend fun cancelMatchmaking()
    suspend fun createPrivateRoom(playerCount: Int, requestId: String): OnlineRoomSnapshot
    suspend fun joinPrivateRoom(roomCode: String, requestId: String): OnlineRoomSnapshot
    suspend fun setRoomReady(roomId: String, ready: Boolean, requestId: String)
    suspend fun updateRoomRules(
        roomId: String,
        turnDurationMillis: Long,
        allowWildDrawFourChallenge: Boolean,
        requestId: String
    ) = Unit
    suspend fun leaveRoom(roomId: String, requestId: String)
    suspend fun startRoom(roomId: String, requestId: String): String
    suspend fun reconnectToMatch(matchId: String)
    suspend fun heartbeatMatch(matchId: String)
    suspend fun submitAction(
        matchId: String,
        action: String,
        cardId: String?,
        chosenColor: String?,
        expectedTurnNumber: Int,
        expectedStateVersion: Int,
        requestId: String
    )
    suspend fun processExpiredTurn(matchId: String, requestId: String): Boolean
    suspend fun getHistory(
        pageSize: Int,
        cursor: OnlineMatchHistoryCursor?
    ): OnlineMatchHistoryPage
}
