package com.arenacards.data

import com.arenacards.feature.multiplayer.MultiplayerRepository
import com.arenacards.feature.multiplayer.OnlineCard
import com.arenacards.feature.multiplayer.OnlineMatchHistoryCursor
import com.arenacards.feature.multiplayer.OnlineMatchHistoryEntry
import com.arenacards.feature.multiplayer.OnlineMatchHistoryPage
import com.arenacards.feature.multiplayer.OnlineMatchPosition
import com.arenacards.feature.multiplayer.OnlineMatchResult
import com.arenacards.feature.multiplayer.OnlineMatchSnapshot
import com.arenacards.feature.multiplayer.OnlinePlayer
import com.arenacards.feature.multiplayer.OnlinePlayerStatus
import com.arenacards.feature.multiplayer.OnlineRoomPlayer
import com.arenacards.feature.multiplayer.OnlineRoomSnapshot
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseMultiplayerRepository : MultiplayerRepository {
    private val app = FirebaseApp.getInstance()
    private val auth = FirebaseAuth.getInstance(app)
    private val firestore = FirebaseFirestoreProvider.getInstance(app)
    private val functions = FirebaseFunctions.getInstance(
        app,
        FirebaseBackendConfig.FUNCTIONS_REGION
    )
    override val currentUid: String
        get() = requireUid()

    override fun observePlayerStatus(): Flow<OnlinePlayerStatus> = callbackFlow {
        val uid = requireUid()
        val registration = firestore.collection("onlinePlayers").document(uid)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toMultiplayerError())
                    snapshot == null -> close(IllegalStateException("Online player status was empty."))
                    !snapshot.exists() -> trySend(OnlinePlayerStatus())
                    else -> try {
                        val data = snapshot.data.orEmpty()
                        trySend(
                            OnlinePlayerStatus(
                                status = data.requiredString("status"),
                                roomId = data.optionalString("roomId"),
                                matchId = data.optionalString("activeMatchId"),
                                lastMatchId = data.optionalString("lastMatchId"),
                                queueExpiresAtMillis = data.optionalLong("queueExpiresAtMillis")
                            )
                        )
                    } catch (mappingError: Exception) {
                        close(IllegalStateException("Online player status was invalid.", mappingError))
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeRoom(roomId: String): Flow<OnlineRoomSnapshot?> = callbackFlow {
        requireUid()
        val registration = firestore.collection("privateRooms").document(roomId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toMultiplayerError())
                    snapshot == null -> close(IllegalStateException("Private room response was empty."))
                    !snapshot.exists() -> trySend(null)
                    else -> try {
                        trySend(snapshot.data.orEmpty().toRoomSnapshot())
                    } catch (mappingError: Exception) {
                        close(IllegalStateException("Private room data was invalid.", mappingError))
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeMatch(matchId: String): Flow<OnlineMatchSnapshot> = callbackFlow {
        val uid = requireUid()
        val matchDocument = firestore.collection("onlineMatches").document(matchId)
        var matchData: Map<String, Any?>? = null
        var handData: Map<String, Any?>? = null
        var matchRegistration: ListenerRegistration? = null
        var handRegistration: ListenerRegistration? = null

        fun publishIfConsistent() {
            val public = matchData ?: return
            val privateHand = handData ?: return
            try {
                val publicVersion = public.requiredInt("stateVersion")
                if (privateHand.requiredInt("stateVersion") != publicVersion) return
                trySend(
                    public.toMatchSnapshot(
                        hand = privateHand.requiredList("cards").map {
                            it.asMap("private card").toOnlineCard()
                        },
                        drawnCardId = privateHand.optionalString("drawnCardId")
                    )
                )
            } catch (mappingError: Exception) {
                close(IllegalStateException("Online match data was invalid.", mappingError))
            }
        }

        matchRegistration = matchDocument.addSnapshotListener { snapshot, error ->
            when {
                error != null -> close(error.toMultiplayerError())
                snapshot == null -> close(IllegalStateException("Online match response was empty."))
                !snapshot.exists() -> close(IllegalStateException("This online match is no longer available."))
                else -> {
                    matchData = snapshot.data
                    publishIfConsistent()
                }
            }
        }
        handRegistration = matchDocument.collection("hands").document(uid)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toMultiplayerError())
                    snapshot == null -> close(IllegalStateException("Private hand response was empty."))
                    !snapshot.exists() -> close(IllegalStateException("Your private hand is unavailable."))
                    else -> {
                        handData = snapshot.data
                        publishIfConsistent()
                    }
                }
            }
        awaitClose {
            matchRegistration?.remove()
            handRegistration?.remove()
        }
    }

    override suspend fun joinMatchmaking(playerCount: Int, requestId: String): OnlinePlayerStatus {
        val response = call(
            "joinMatchmaking",
            mapOf("playerCount" to playerCount, "requestId" to requestId)
        ).asMap("matchmaking response")
        return OnlinePlayerStatus(
            status = if (response.requiredBoolean("matched")) "IN_MATCH" else "QUEUED",
            matchId = response.optionalString("matchId")
        )
    }

    override suspend fun cancelMatchmaking() {
        call("cancelMatchmaking", emptyMap())
    }

    override suspend fun createPrivateRoom(
        playerCount: Int,
        requestId: String
    ): OnlineRoomSnapshot {
        val response = call(
            "createPrivateRoom",
            mapOf("playerCount" to playerCount, "requestId" to requestId)
        ).asMap("private room response")
        return getPrivateRoom(response.requiredString("roomId"))
    }

    override suspend fun joinPrivateRoom(roomCode: String, requestId: String): OnlineRoomSnapshot {
        val response = call(
            "joinPrivateRoom",
            mapOf("roomCode" to roomCode, "requestId" to requestId)
        ).asMap("private room response")
        return getPrivateRoom(response.requiredString("roomId"))
    }

    override suspend fun setRoomReady(roomId: String, ready: Boolean, requestId: String) {
        call(
            "setPrivateRoomReady",
            mapOf("roomId" to roomId, "ready" to ready, "requestId" to requestId)
        )
    }

    override suspend fun updateRoomRules(
        roomId: String,
        turnDurationMillis: Long,
        allowWildDrawFourChallenge: Boolean,
        requestId: String
    ) {
        call(
            "updatePrivateRoomRules",
            mapOf(
                "roomId" to roomId,
                "turnDurationMillis" to turnDurationMillis,
                "allowWildDrawFourChallenge" to allowWildDrawFourChallenge,
                "requestId" to requestId
            )
        )
    }

    override suspend fun leaveRoom(roomId: String, requestId: String) {
        call("leavePrivateRoom", mapOf("roomId" to roomId, "requestId" to requestId))
    }

    override suspend fun startRoom(roomId: String, requestId: String): String =
        call("startPrivateRoom", mapOf("roomId" to roomId, "requestId" to requestId))
            .asMap("start match response")
            .requiredString("matchId")

    override suspend fun reconnectToMatch(matchId: String) {
        call("reconnectToMatch", mapOf("matchId" to matchId))
    }

    override suspend fun heartbeatMatch(matchId: String) {
        call("heartbeatOnlineMatch", mapOf("matchId" to matchId))
    }

    override suspend fun submitAction(
        matchId: String,
        action: String,
        cardId: String?,
        chosenColor: String?,
        expectedTurnNumber: Int,
        expectedStateVersion: Int,
        requestId: String
    ) {
        val payload = mutableMapOf<String, Any>(
            "matchId" to matchId,
            "action" to action,
            "expectedTurnNumber" to expectedTurnNumber,
            "expectedStateVersion" to expectedStateVersion,
            "requestId" to requestId
        )
        cardId?.let { payload["cardId"] = it }
        chosenColor?.let { payload["chosenColor"] = it }
        call("submitMatchAction", payload)
    }

    override suspend fun processExpiredTurn(matchId: String, requestId: String): Boolean =
        call(
            "processExpiredTurn",
            mapOf("matchId" to matchId, "requestId" to requestId)
        ).asMap("turn timeout response").requiredBoolean("expired")

    override suspend fun getHistory(
        pageSize: Int,
        cursor: OnlineMatchHistoryCursor?
    ): OnlineMatchHistoryPage {
        val payload = mutableMapOf<String, Any>("pageSize" to pageSize)
        cursor?.let {
            payload["beforeCompletedAtMillis"] = it.beforeCompletedAtMillis
            payload["beforeMatchId"] = it.beforeMatchId
        }
        val response = call("getMatchHistory", payload).asMap("match history response")
        val next = response["nextCursor"]?.let {
            val values = it.asMap("match history cursor")
            OnlineMatchHistoryCursor(
                beforeCompletedAtMillis = values.requiredLong("beforeCompletedAtMillis"),
                beforeMatchId = values.requiredString("beforeMatchId")
            )
        }
        return OnlineMatchHistoryPage(
            matches = response.requiredList("matches").map { item ->
                val match = item.asMap("match history item")
                OnlineMatchHistoryEntry(
                    matchId = match.requiredString("matchId"),
                    status = match.requiredString("status"),
                    verified = match.requiredBoolean("verified"),
                    outcome = match.requiredString("outcome"),
                    winnerUid = match.optionalString("winnerUid"),
                    finalPosition = match.optionalInt("finalPosition"),
                    durationMillis = match.requiredLong("durationMillis"),
                    completionReason = match.requiredString("completionReason"),
                    completedAtMillis = match.optionalLong("completedAtMillis"),
                    players = match.requiredList("players").map {
                        it.asMap("history player").toOnlinePlayer()
                    }
                )
            },
            nextCursor = next
        )
    }

    private suspend fun getPrivateRoom(roomId: String): OnlineRoomSnapshot =
        call("getPrivateRoom", mapOf("roomId" to roomId))
            .asMap("private room response")
            .toRoomSnapshot()

    private suspend fun call(name: String, payload: Map<String, Any>): Any? {
        requireUid()
        return try {
            functions.getHttpsCallable(name).call(payload).await().getData()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseFunctionsException) {
            throw error.toMultiplayerError()
        } catch (error: Exception) {
            throw IllegalStateException(
                error.message ?: "The multiplayer service could not complete the request.",
                error
            )
        }
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Sign in to play online.")

    private fun Any?.asMap(label: String): Map<String, Any?> {
        val source = this as? Map<*, *>
            ?: throw IllegalStateException("The $label was invalid.")
        return source.entries.associate { (key, value) ->
            (key as? String ?: throw IllegalStateException("The $label contained an invalid field.")) to value
        }
    }

    private fun Any?.asStringList(label: String): List<String> =
        (this as? List<*>)?.map { it as? String
            ?: throw IllegalStateException("The $label contained an invalid value.") }
            ?: throw IllegalStateException("The $label was invalid.")

    private fun Map<String, Any?>.toRoomSnapshot(): OnlineRoomSnapshot =
        OnlineRoomSnapshot(
            roomId = requiredString("roomId"),
            roomCode = requiredString("roomCode"),
            ownerUid = requiredString("ownerUid"),
            memberUids = get("memberUids").asStringList("room members"),
            players = requiredList("players").map { it.asMap("room player").let { player ->
                OnlineRoomPlayer(
                    uid = player.requiredString("uid"),
                    displayName = player.requiredString("displayName"),
                    username = player.requiredString("username"),
                    gamerId = player.requiredString("gamerId"),
                    avatarId = player.requiredString("avatarId"),
                    ready = player.requiredBoolean("ready")
                )
            } },
            capacity = requiredInt("capacity"),
            status = requiredString("status"),
            expiresAtMillis = requiredLong("expiresAtMillis"),
            matchId = optionalString("matchId"),
            turnDurationMillis = (get("rules") as? Map<*, *>)?.get("turnDurationMillis")
                .toLongOrDefault(45_000),
            allowWildDrawFourChallenge =
                ((get("rules") as? Map<*, *>)?.get("allowWildDrawFourChallenge") as? Boolean) ?: true
        )

    private fun Any?.toLongOrDefault(default: Long): Long = (this as? Number)?.toLong() ?: default

    private fun Map<String, Any?>.toMatchSnapshot(
        hand: List<OnlineCard>,
        drawnCardId: String?
    ): OnlineMatchSnapshot {
        val resultMap = (this["result"] as? Map<*, *>)?.asMap("match result")
        val result = resultMap?.let { value ->
            OnlineMatchResult(
                verified = value.requiredBoolean("verified"),
                winnerUid = value.optionalString("winnerUid"),
                completedAtMillis = value.optionalLong("completedAtMillis"),
                durationMillis = value.requiredLong("durationMillis"),
                completionReason = value.requiredString("completionReason"),
                positions = value.requiredList("positions").map { item ->
                    val position = item.asMap("match position")
                    OnlineMatchPosition(
                        uid = position.requiredString("uid"),
                        position = position.requiredInt("position"),
                        remainingCards = position.requiredInt("remainingCards")
                    )
                }
            )
        }
        return OnlineMatchSnapshot(
            matchId = requiredString("matchId"),
            status = requiredString("status"),
            mode = requiredString("mode"),
            playerUids = get("playerUids").asStringList("match players"),
            players = requiredList("players").map {
                it.asMap("match player").toOnlinePlayer()
            },
            currentPlayerUid = optionalString("currentPlayerUid"),
            turnNumber = requiredInt("turnNumber"),
            stateVersion = requiredInt("stateVersion"),
            direction = requiredInt("direction"),
            activeColor = requiredString("activeColor"),
            topCard = requiredMap("topCard").toOnlineCard(),
            discardHistory = requiredList("discardHistory").map {
                it.asMap("discard history card").toOnlineCard()
            },
            drawPileCount = requiredInt("drawPileCount"),
            hasDrawnThisTurn = requiredBoolean("hasDrawnThisTurn"),
            challengePending = requiredBoolean("challengePending"),
            challengeTargetUid = optionalString("challengeTargetUid"),
            drawnCardId = drawnCardId,
            turnDeadlineMillis = optionalLong("turnDeadlineMillis"),
            createdAtMillis = requiredLong("createdAtMillis"),
            result = result,
            completionReason = optionalString("completionReason"),
            hand = hand,
            pendingUnoPlayerUid = optionalString("pendingUnoPlayerUid"),
            unoDeadlineMillis = optionalLong("unoDeadlineMillis")
        )
    }

    private fun Map<String, Any?>.toOnlineCard(): OnlineCard = OnlineCard(
        id = requiredString("id"),
        color = requiredString("color"),
        value = requiredInt("value"),
        isWild = requiredBoolean("isWild")
    )

    private fun Map<String, Any?>.toOnlinePlayer(): OnlinePlayer = OnlinePlayer(
        uid = requiredString("uid"),
        displayName = requiredString("displayName"),
        username = optionalString("username").orEmpty(),
        gamerId = optionalString("gamerId").orEmpty(),
        avatarId = optionalString("avatarId").orEmpty(),
        seat = optionalInt("seat") ?: 0,
        cardCount = optionalInt("cardCount") ?: 0,
        timeoutCount = optionalInt("timeoutCount") ?: 0,
        connectionState = optionalString("connectionState") ?: "UNKNOWN",
        lastSeenAtMillis = optionalLong("lastSeenAtMillis")
    )

    private fun Map<String, Any?>.requiredMap(key: String): Map<String, Any?> =
        get(key).asMap(key)

    private fun Map<String, Any?>.requiredList(key: String): List<*> =
        get(key) as? List<*> ?: throw IllegalStateException("The multiplayer field '$key' was invalid.")

    private fun Map<String, Any?>.requiredString(key: String): String =
        get(key) as? String ?: throw IllegalStateException("The multiplayer field '$key' was invalid.")

    private fun Map<String, Any?>.optionalString(key: String): String? =
        get(key) as? String

    private fun Map<String, Any?>.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: throw IllegalStateException("The multiplayer field '$key' was invalid.")

    private fun Map<String, Any?>.requiredInt(key: String): Int =
        (get(key) as? Number)?.toInt()
            ?: throw IllegalStateException("The multiplayer field '$key' was invalid.")

    private fun Map<String, Any?>.optionalInt(key: String): Int? =
        (get(key) as? Number)?.toInt()

    private fun Map<String, Any?>.requiredLong(key: String): Long =
        (get(key) as? Number)?.toLong()
            ?: throw IllegalStateException("The multiplayer field '$key' was invalid.")

    private fun Map<String, Any?>.optionalLong(key: String): Long? =
        when (val value = get(key)) {
            is Number -> value.toLong()
            is Timestamp -> value.toDate().time
            else -> null
        }

    private fun Exception.toMultiplayerError(): Exception {
        val code = (this as? FirebaseFunctionsException)?.code
        val message = when (code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Sign in again to continue online."
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> "The multiplayer service denied this request."
            FirebaseFunctionsException.Code.UNAVAILABLE -> "Multiplayer is temporarily unavailable. Try again."
            FirebaseFunctionsException.Code.ABORTED -> "The match changed. The table is refreshing."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "Please wait before trying that again."
            else -> this.message ?: "The multiplayer service could not complete the request."
        }
        return IllegalStateException(message, this)
    }
}
