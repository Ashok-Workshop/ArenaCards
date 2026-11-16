package com.arenacards.data

import android.content.Context
import java.util.UUID
import com.arenacards.feature.friends.FriendRequest
import com.arenacards.feature.friends.RoomInvitation
import com.arenacards.feature.friends.SocialConversation
import com.arenacards.feature.friends.SocialMessage
import com.arenacards.feature.friends.SocialMessagePage
import com.arenacards.feature.friends.SocialNotification
import com.arenacards.feature.friends.SocialNotificationPreferences
import com.arenacards.feature.friends.SocialPlayer
import com.arenacards.feature.friends.SocialListPage
import com.arenacards.feature.friends.SocialRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseSocialRepository : SocialRepository {
    private val app = FirebaseApp.getInstance()
    private val auth = FirebaseAuth.getInstance(app)
    private val firestore = FirebaseFirestoreProvider.getInstance(app)
    private val functions = FirebaseFunctions.getInstance(app, FirebaseBackendConfig.FUNCTIONS_REGION)

    override val currentUid: String
        get() = requireUid()

    override suspend fun updatePresence(foreground: Boolean) {
        call("updatePlayerPresence", mapOf("foreground" to foreground))
    }

    override suspend fun searchPlayer(gamerId: String): SocialPlayer? {
        val response = call("searchPlayerByGamerId", mapOf("gamerId" to gamerId))
            .asMap("player search")
        return (response["player"] as? Map<*, *>)?.asMap("player result")?.toSocialPlayer()
    }

    override suspend fun sendFriendRequest(gamerId: String, requestId: String) {
        call("sendFriendRequest", mapOf("gamerId" to gamerId, "requestId" to requestId))
    }

    override suspend fun listFriendRequests(
        direction: String,
        cursor: String?
    ): SocialListPage<FriendRequest> {
        val payload = mapOf("direction" to direction, "pageSize" to PAGE_SIZE) +
            (cursor?.let { mapOf("cursorUid" to it) } ?: emptyMap())
        val response = call("listFriendRequests", payload).asMap("friend requests")
        return SocialListPage(
            items = response.requiredList("requests").map { item ->
                val value = item.asMap("friend request")
                FriendRequest(
                    player = value.toSocialPlayer(),
                    requestId = value.requiredString("requestId")
                )
            },
            nextCursor = response.optionalString("nextCursorUid")
        )
    }

    override suspend fun acceptFriendRequest(request: FriendRequest) {
        call(
            "acceptFriendRequest",
            mapOf("requesterUid" to request.player.uid, "requestId" to request.requestId)
        )
    }

    override suspend fun rejectFriendRequest(request: FriendRequest) {
        call(
            "rejectFriendRequest",
            mapOf("requesterUid" to request.player.uid, "requestId" to request.requestId)
        )
    }

    override suspend fun cancelFriendRequest(request: FriendRequest) {
        call(
            "cancelFriendRequest",
            mapOf("recipientUid" to request.player.uid, "requestId" to request.requestId)
        )
    }

    override suspend fun listFriends(cursor: String?): SocialListPage<SocialPlayer> {
        val payload = mapOf("pageSize" to PAGE_SIZE) +
            (cursor?.let { mapOf("cursorUid" to it) } ?: emptyMap())
        val response = call("listFriends", payload).asMap("friends")
        return SocialListPage(
            items = response.requiredList("friends").map { item ->
                val value = item.asMap("friend")
                value.requiredMap("player").toSocialPlayer().copy(
                    presence = value.optionalString("presence") ?: "OFFLINE"
                )
            },
            nextCursor = response.optionalString("nextCursorUid")
        )
    }

    override suspend fun removeFriend(uid: String) {
        call("removeFriend", mapOf("friendUid" to uid))
    }

    override suspend fun blockPlayer(uid: String) {
        call("blockPlayer", mapOf("playerUid" to uid))
    }

    override suspend fun unblockPlayer(uid: String) {
        call("unblockPlayer", mapOf("playerUid" to uid))
    }

    override suspend fun listBlockedPlayers(cursor: String?): SocialListPage<SocialPlayer> {
        val payload = mapOf("pageSize" to PAGE_SIZE) +
            (cursor?.let { mapOf("cursorUid" to it) } ?: emptyMap())
        val response = call("listBlockedPlayers", payload).asMap("blocked players")
        return SocialListPage(
            items = response.requiredList("players").map { it.asMap("blocked player").toSocialPlayer() },
            nextCursor = response.optionalString("nextCursorUid")
        )
    }

    override suspend fun listRoomInvitations(cursor: String?): SocialListPage<RoomInvitation> {
        val payload = mapOf("pageSize" to PAGE_SIZE) +
            (cursor?.let { mapOf("cursorId" to it) } ?: emptyMap())
        val response = call("listRoomInvitations", payload).asMap("room invitations")
        return SocialListPage(
            items = response.requiredList("invitations").map { item ->
                val value = item.asMap("room invitation")
                RoomInvitation(
                    invitationId = value.requiredString("invitationId"),
                    roomId = value.requiredString("roomId"),
                    roomCode = value.requiredString("roomCode"),
                    inviter = (value["inviter"] as? Map<*, *>)?.asMap("inviter")?.toSocialPlayer(),
                    expiresAtMillis = value.requiredLong("expiresAtMillis")
                )
            },
            nextCursor = response.optionalString("nextCursorId")
        )
    }

    override suspend fun acceptRoomInvitation(invitationId: String): String =
        call("acceptRoomInvitation", mapOf("invitationId" to invitationId))
            .asMap("accepted room invitation")
            .requiredString("roomId")

    override suspend fun rejectRoomInvitation(invitationId: String) {
        call("rejectRoomInvitation", mapOf("invitationId" to invitationId))
    }

    override suspend fun inviteFriendToRoom(roomId: String, friendUid: String, requestId: String) {
        call(
            "inviteFriendToRoom",
            mapOf("roomId" to roomId, "friendUid" to friendUid, "requestId" to requestId)
        )
    }

    override fun observeConversations(): Flow<List<SocialConversation>> = callbackFlow {
        val uid = requireUid()
        val registration = firestore.collection("conversations")
            .whereArrayContains("memberUids", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE.toLong())
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSocialError())
                    snapshot == null -> close(IllegalStateException("Conversation list was empty."))
                    else -> launch {
                        try {
                            val conversations = snapshot.documents.map { document ->
                                val friendUid = document.get("memberUids")
                                    .asStringList("conversation members")
                                    .firstOrNull { it != uid }
                                    ?: throw IllegalStateException("Conversation had no other participant.")
                                val friend = try {
                                    firestore.collection("publicPlayers").document(friendUid).get().await()
                                        .takeIf { it.exists() }
                                        ?.data
                                        ?.toSocialPlayer()
                                        ?: SocialPlayer(friendUid, "Arena player", "", "")
                                } catch (profileError: FirebaseFirestoreException) {
                                    if (profileError.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                        throw profileError
                                    }
                                    SocialPlayer(friendUid, "Private player", "", "")
                                }
                                val unreadCounts = document.get("unreadCounts").asMapOrEmpty()
                                SocialConversation(
                                    conversationId = document.id,
                                    friend = friend,
                                    lastMessagePreview = document.getString("lastMessagePreview").orEmpty(),
                                    unreadCount = (unreadCounts[uid] as? Number)?.toInt() ?: 0
                                )
                            }
                            trySend(conversations)
                        } catch (mappingError: Exception) {
                            close(IllegalStateException("Conversation data was invalid.", mappingError))
                        }
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun openConversation(friendUid: String): String =
        call("openDirectConversation", mapOf("friendUid" to friendUid))
            .asMap("direct conversation")
            .requiredString("conversationId")

    override fun observeMessages(conversationId: String): Flow<List<SocialMessage>> = callbackFlow {
        requireUid()
        val registration = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(MESSAGE_PAGE_SIZE.toLong())
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSocialError())
                    snapshot == null -> close(IllegalStateException("Message list was empty."))
                    else -> try {
                        trySend(snapshot.documents.map { document ->
                            SocialMessage(
                                messageId = document.id,
                                senderUid = document.getString("senderUid")
                                    ?: throw IllegalStateException("Message sender was missing."),
                                text = document.getString("text")
                                    ?: throw IllegalStateException("Message text was missing."),
                                createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time
                            )
                        })
                    } catch (mappingError: Exception) {
                        close(IllegalStateException("Message data was invalid.", mappingError))
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getOlderMessages(
        conversationId: String,
        beforeCreatedAtMillis: Long,
        beforeMessageId: String
    ): SocialMessagePage {
        val response = call(
            "getDirectMessages",
            mapOf(
                "conversationId" to conversationId,
                "pageSize" to MESSAGE_PAGE_SIZE,
                "beforeCreatedAtMillis" to beforeCreatedAtMillis,
                "beforeMessageId" to beforeMessageId
            )
        ).asMap("older messages")
        val cursor = (response["nextCursor"] as? Map<*, *>)?.asMap("message cursor")
        return SocialMessagePage(
            messages = response.requiredList("messages").map { item ->
                val value = item.asMap("message")
                SocialMessage(
                    messageId = value.requiredString("messageId"),
                    senderUid = value.requiredString("senderUid"),
                    text = value.requiredString("text"),
                    createdAtMillis = value.optionalLong("createdAtMillis")
                )
            },
            nextCursorCreatedAtMillis = cursor?.optionalLong("beforeCreatedAtMillis"),
            nextCursorMessageId = cursor?.optionalString("beforeMessageId")
        )
    }

    override fun observeRoomMessages(roomId: String): Flow<List<SocialMessage>> = callbackFlow {
        requireUid()
        val registration = firestore.collection("privateRooms")
            .document(roomId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(MESSAGE_PAGE_SIZE.toLong())
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSocialError())
                    snapshot == null -> close(IllegalStateException("Room message list was empty."))
                    else -> try {
                        trySend(snapshot.documents.map { document ->
                            SocialMessage(
                                messageId = document.id,
                                senderUid = document.getString("senderUid")
                                    ?: throw IllegalStateException("Message sender was missing."),
                                text = document.getString("text")
                                    ?: throw IllegalStateException("Message text was missing."),
                                createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time
                            )
                        })
                    } catch (mappingError: Exception) {
                        close(IllegalStateException("Room message data was invalid.", mappingError))
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getOlderRoomMessages(
        roomId: String,
        beforeCreatedAtMillis: Long,
        beforeMessageId: String
    ): SocialMessagePage {
        val response = call(
            "getPrivateRoomMessages",
            mapOf(
                "roomId" to roomId,
                "pageSize" to MESSAGE_PAGE_SIZE,
                "beforeCreatedAtMillis" to beforeCreatedAtMillis,
                "beforeMessageId" to beforeMessageId
            )
        ).asMap("older room messages")
        val cursor = (response["nextCursor"] as? Map<*, *>)?.asMap("room message cursor")
        return SocialMessagePage(
            messages = response.requiredList("messages").map { item ->
                val value = item.asMap("room message")
                SocialMessage(
                    messageId = value.requiredString("messageId"),
                    senderUid = value.requiredString("senderUid"),
                    text = value.requiredString("text"),
                    createdAtMillis = value.optionalLong("createdAtMillis")
                )
            },
            nextCursorCreatedAtMillis = cursor?.optionalLong("beforeCreatedAtMillis"),
            nextCursorMessageId = cursor?.optionalString("beforeMessageId")
        )
    }

    override suspend fun sendDirectMessage(conversationId: String, text: String, requestId: String) {
        call(
            "sendDirectMessage",
            mapOf("conversationId" to conversationId, "text" to text, "requestId" to requestId)
        )
    }

    override suspend fun sendRoomMessage(roomId: String, text: String, requestId: String) {
        call(
            "sendPrivateRoomMessage",
            mapOf("roomId" to roomId, "text" to text, "requestId" to requestId)
        )
    }

    override suspend fun markConversationRead(conversationId: String) {
        call("markConversationRead", mapOf("conversationId" to conversationId))
    }

    override suspend fun reportPlayer(
        reportedUid: String,
        reason: String,
        details: String,
        messageId: String?,
        requestId: String
    ) {
        val payload = mutableMapOf<String, Any>(
            "reportedUid" to reportedUid,
            "reason" to reason,
            "details" to details,
            "requestId" to requestId
        )
        messageId?.let { payload["messageId"] = it }
        call("reportPlayer", payload)
    }

    override suspend fun listNotifications(cursor: String?): SocialListPage<SocialNotification> {
        val cursorParts = cursor?.split(CURSOR_SEPARATOR, limit = 2)
        if (cursor != null && (cursorParts?.size != 2 || cursorParts[0].toLongOrNull() == null)) {
            throw IllegalArgumentException("Notification cursor is invalid.")
        }
        val payload = mapOf("pageSize" to PAGE_SIZE) + if (cursorParts == null) {
            emptyMap()
        } else {
            mapOf(
                "cursorCreatedAtMillis" to requireNotNull(cursorParts[0].toLongOrNull()),
                "cursorId" to cursorParts[1]
            )
        }
        val response = call("listInAppNotifications", payload).asMap("notifications")
        val next = (response["nextCursor"] as? Map<*, *>)?.asMap("notification cursor")
        val nextCursor = next?.let {
            "${it.requiredLong("cursorCreatedAtMillis")}$CURSOR_SEPARATOR${it.requiredString("cursorId")}"
        }
        return SocialListPage(
            items = response.requiredList("notifications").map { item ->
                val value = item.asMap("notification")
                SocialNotification(
                    notificationId = value.requiredString("notificationId"),
                    type = value.requiredString("type"),
                    actorUid = value.optionalString("actorUid").orEmpty(),
                    isRead = value.requiredBoolean("isRead"),
                    createdAtMillis = value.optionalLong("createdAtMillis")
                )
            },
            nextCursor = nextCursor
        )
    }

    override suspend fun markNotificationRead(notificationId: String) {
        call("markInAppNotificationRead", mapOf("notificationId" to notificationId))
    }

    override suspend fun getNotificationPreferences(): SocialNotificationPreferences {
        val value = call("getNotificationPreferences", emptyMap<String, Any>())
            .asMap("notification preferences")
        return SocialNotificationPreferences(
            friendRequests = value.requiredBoolean("friendRequests"),
            roomInvitations = value.requiredBoolean("roomInvitations"),
            messages = value.requiredBoolean("messages"),
            matchUpdates = value.requiredBoolean("matchUpdates"),
            socialUpdates = value.requiredBoolean("socialUpdates")
        )
    }

    override suspend fun setNotificationPreferences(preferences: SocialNotificationPreferences) {
        call(
            "setNotificationPreferences",
            mapOf(
                "friendRequests" to preferences.friendRequests,
                "roomInvitations" to preferences.roomInvitations,
                "messages" to preferences.messages,
                "matchUpdates" to preferences.matchUpdates,
                "socialUpdates" to preferences.socialUpdates
            )
        )
    }

    override suspend fun registerNotificationToken(token: String, deviceId: String) {
        call("registerNotificationToken", mapOf("token" to token, "deviceId" to deviceId))
    }

    suspend fun registerCurrentNotificationToken(context: Context) {
        val token = FirebaseMessaging.getInstance().token.await()
        registerNotificationToken(token, FirebaseNotificationDeviceId.get(context))
    }

    override suspend fun removeNotificationToken(deviceId: String) {
        call("removeNotificationToken", mapOf("deviceId" to deviceId))
    }

    private suspend fun call(name: String, payload: Map<String, Any>): Any? {
        requireUid()
        return try {
            functions.getHttpsCallable(name).call(payload).await().getData()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseFunctionsException) {
            throw error.toSocialError()
        } catch (error: Exception) {
            throw IllegalStateException(
                error.message ?: "The social service could not complete the request.",
                error
            )
        }

    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Sign in to use social features.")

    private fun Any?.asMap(label: String): Map<String, Any?> {
        val source = this as? Map<*, *>
            ?: throw IllegalStateException("The $label was invalid.")
        return source.entries.associate { (key, value) ->
            (key as? String ?: throw IllegalStateException("The $label contained an invalid field.")) to value
        }
    }

    private fun Any?.asMapOrEmpty(): Map<String, Any?> =
        (this as? Map<*, *>)?.entries?.associate { (key, value) ->
            (key as? String ?: "") to value
        }.orEmpty()

    private fun Any?.asStringList(label: String): List<String> =
        (this as? List<*>)?.map { it as? String
            ?: throw IllegalStateException("The $label contained an invalid value.") }
            ?: throw IllegalStateException("The $label was invalid.")

    private fun Map<String, Any?>.toSocialPlayer(): SocialPlayer = SocialPlayer(
        uid = requiredString("uid"),
        displayName = requiredString("displayName"),
        username = optionalString("username").orEmpty(),
        gamerId = optionalString("gamerId").orEmpty(),
        avatarId = optionalString("avatarId") ?: "ember"
    )

    private fun Map<String, Any?>.requiredMap(key: String): Map<String, Any?> =
        get(key).asMap(key)

    private fun Map<String, Any?>.requiredList(key: String): List<*> =
        get(key) as? List<*> ?: throw IllegalStateException("The social field '$key' was invalid.")

    private fun Map<String, Any?>.requiredString(key: String): String =
        get(key) as? String ?: throw IllegalStateException("The social field '$key' was invalid.")

    private fun Map<String, Any?>.optionalString(key: String): String? =
        get(key) as? String

    private fun Map<String, Any?>.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: throw IllegalStateException("The social field '$key' was invalid.")

    private fun Map<String, Any?>.requiredLong(key: String): Long =
        (get(key) as? Number)?.toLong()
            ?: throw IllegalStateException("The social field '$key' was invalid.")

    private fun Map<String, Any?>.optionalLong(key: String): Long? =
        when (val value = get(key)) {
            is Number -> value.toLong()
            is Timestamp -> value.toDate().time
            else -> null
        }

    private fun Exception.toSocialError(): Exception {
        val code = (this as? FirebaseFunctionsException)?.code
        val message = when (code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Sign in again to continue."
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> "This social action is not permitted."
            FirebaseFunctionsException.Code.UNAVAILABLE -> "Social features are temporarily unavailable."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "Please wait before trying again."
            else -> this.message ?: "The social service could not complete the request."
        }
        return IllegalStateException(message, this)
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val MESSAGE_PAGE_SIZE = 50
        const val CURSOR_SEPARATOR = "|"
    }
}

object FirebaseNotificationDeviceId {
    fun get(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val existing = preferences.getString(DEVICE_ID_KEY, null)
        if (existing != null && DEVICE_ID_REGEX.matches(existing)) return existing
        val generated = UUID.randomUUID().toString()
        check(preferences.edit().putString(DEVICE_ID_KEY, generated).commit()) {
            "The notification device ID could not be saved."
        }
        return generated
    }

    private const val PREFERENCES_NAME = "arena_cards_notification_device"
    private const val DEVICE_ID_KEY = "device_id"
    private val DEVICE_ID_REGEX = Regex("[A-Za-z0-9_-]{8,100}")
}
