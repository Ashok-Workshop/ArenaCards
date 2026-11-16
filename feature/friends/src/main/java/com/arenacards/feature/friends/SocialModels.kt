package com.arenacards.feature.friends

import kotlinx.coroutines.flow.Flow

data class SocialPlayer(
    val uid: String,
    val displayName: String,
    val username: String,
    val gamerId: String,
    val avatarId: String = "ember",
    val presence: String = "OFFLINE"
)

data class FriendRequest(
    val player: SocialPlayer,
    val requestId: String
)

data class SocialListPage<T>(
    val items: List<T>,
    val nextCursor: String?
)

data class RoomInvitation(
    val invitationId: String,
    val roomId: String,
    val roomCode: String,
    val inviter: SocialPlayer?,
    val expiresAtMillis: Long
)

data class SocialConversation(
    val conversationId: String,
    val friend: SocialPlayer,
    val lastMessagePreview: String,
    val unreadCount: Int
)

data class SocialMessage(
    val messageId: String,
    val senderUid: String,
    val text: String,
    val createdAtMillis: Long?
)

data class SocialMessagePage(
    val messages: List<SocialMessage>,
    val nextCursorCreatedAtMillis: Long?,
    val nextCursorMessageId: String?
)

data class SocialNotification(
    val notificationId: String,
    val type: String,
    val actorUid: String,
    val isRead: Boolean,
    val createdAtMillis: Long?
)

data class SocialNotificationPreferences(
    val friendRequests: Boolean = true,
    val roomInvitations: Boolean = true,
    val messages: Boolean = true,
    val matchUpdates: Boolean = true,
    val socialUpdates: Boolean = true
)

interface SocialRepository {
    val currentUid: String

    suspend fun updatePresence(foreground: Boolean)
    suspend fun searchPlayer(gamerId: String): SocialPlayer?
    suspend fun sendFriendRequest(gamerId: String, requestId: String)
    suspend fun listFriendRequests(direction: String, cursor: String? = null): SocialListPage<FriendRequest>
    suspend fun acceptFriendRequest(request: FriendRequest)
    suspend fun rejectFriendRequest(request: FriendRequest)
    suspend fun cancelFriendRequest(request: FriendRequest)
    suspend fun listFriends(cursor: String? = null): SocialListPage<SocialPlayer>
    suspend fun removeFriend(uid: String)
    suspend fun blockPlayer(uid: String)
    suspend fun unblockPlayer(uid: String)
    suspend fun listBlockedPlayers(cursor: String? = null): SocialListPage<SocialPlayer>
    suspend fun listRoomInvitations(cursor: String? = null): SocialListPage<RoomInvitation>
    suspend fun acceptRoomInvitation(invitationId: String): String
    suspend fun rejectRoomInvitation(invitationId: String)
    suspend fun inviteFriendToRoom(roomId: String, friendUid: String, requestId: String)

    fun observeConversations(): Flow<List<SocialConversation>>
    suspend fun openConversation(friendUid: String): String
    fun observeMessages(conversationId: String): Flow<List<SocialMessage>>
    fun observeRoomMessages(roomId: String): Flow<List<SocialMessage>>
    suspend fun getOlderMessages(
        conversationId: String,
        beforeCreatedAtMillis: Long,
        beforeMessageId: String
    ): SocialMessagePage
    suspend fun getOlderRoomMessages(
        roomId: String,
        beforeCreatedAtMillis: Long,
        beforeMessageId: String
    ): SocialMessagePage
    suspend fun sendDirectMessage(conversationId: String, text: String, requestId: String)
    suspend fun sendRoomMessage(roomId: String, text: String, requestId: String)
    suspend fun markConversationRead(conversationId: String)
    suspend fun reportPlayer(
        reportedUid: String,
        reason: String,
        details: String,
        messageId: String?,
        requestId: String
    )

    suspend fun listNotifications(cursor: String? = null): SocialListPage<SocialNotification>
    suspend fun markNotificationRead(notificationId: String)
    suspend fun getNotificationPreferences(): SocialNotificationPreferences
    suspend fun setNotificationPreferences(preferences: SocialNotificationPreferences)
    suspend fun registerNotificationToken(token: String, deviceId: String)
    suspend fun removeNotificationToken(deviceId: String)
}
