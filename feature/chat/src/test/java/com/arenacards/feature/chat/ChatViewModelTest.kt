package com.arenacards.feature.chat

import com.arenacards.feature.friends.FriendRequest
import com.arenacards.feature.friends.RoomInvitation
import com.arenacards.feature.friends.SocialConversation
import com.arenacards.feature.friends.SocialMessage
import com.arenacards.feature.friends.SocialMessagePage
import com.arenacards.feature.friends.SocialListPage
import com.arenacards.feature.friends.SocialNotification
import com.arenacards.feature.friends.SocialNotificationPreferences
import com.arenacards.feature.friends.SocialPlayer
import com.arenacards.feature.friends.SocialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun directMessageIsSentAndMarkedRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeSocialRepository()
        val viewModel = ChatViewModel(repository, initialFriendUid = "friend")
        advanceUntilIdle()

        viewModel.updateDraft("  Hello there  ")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("Hello there", repository.sentText)
        assertEquals("conversation", repository.markedReadConversation)
        assertEquals("", viewModel.uiState.value.draft)
    }

    @Test
    fun olderPageIsPrependedWithoutLosingLiveMessages() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeSocialRepository().apply {
            messages.value = (1L..50L).map { index ->
                SocialMessage("message-$index", "friend", "Message $index", index)
            }
        }
        val viewModel = ChatViewModel(repository, initialFriendUid = "friend")
        advanceUntilIdle()
        assertEquals(50, viewModel.uiState.value.messages.size)

        viewModel.loadOlderMessages()
        advanceUntilIdle()

        assertEquals(51, viewModel.uiState.value.messages.size)
        assertEquals("older-message", viewModel.uiState.value.messages.first().messageId)
        assertEquals("message-50", viewModel.uiState.value.messages.last().messageId)
        assertEquals(false, viewModel.uiState.value.hasOlderMessages)
    }

    private class FakeSocialRepository : SocialRepository {
        override val currentUid = "me"
        val messages = MutableStateFlow<List<SocialMessage>>(emptyList())
        var sentText: String? = null
        var markedReadConversation: String? = null
        private val friend = SocialPlayer("friend", "Friend", "friend", "AR-111111")

        override suspend fun updatePresence(foreground: Boolean) = Unit
        override suspend fun searchPlayer(gamerId: String): SocialPlayer? = friend
        override suspend fun sendFriendRequest(gamerId: String, requestId: String) = Unit
        override suspend fun listFriendRequests(direction: String, cursor: String?) =
            SocialListPage<FriendRequest>(emptyList(), null)
        override suspend fun acceptFriendRequest(request: FriendRequest) = Unit
        override suspend fun rejectFriendRequest(request: FriendRequest) = Unit
        override suspend fun cancelFriendRequest(request: FriendRequest) = Unit
        override suspend fun listFriends(cursor: String?) = SocialListPage(listOf(friend), null)
        override suspend fun removeFriend(uid: String) = Unit
        override suspend fun blockPlayer(uid: String) = Unit
        override suspend fun unblockPlayer(uid: String) = Unit
        override suspend fun listBlockedPlayers(cursor: String?) =
            SocialListPage<SocialPlayer>(emptyList(), null)
        override suspend fun listRoomInvitations(cursor: String?) =
            SocialListPage<RoomInvitation>(emptyList(), null)
        override suspend fun acceptRoomInvitation(invitationId: String) = "room-id"
        override suspend fun rejectRoomInvitation(invitationId: String) = Unit
        override suspend fun inviteFriendToRoom(roomId: String, friendUid: String, requestId: String) = Unit
        override fun observeConversations(): Flow<List<SocialConversation>> = emptyFlow()
        override suspend fun openConversation(friendUid: String) = "conversation"
        override fun observeMessages(conversationId: String): Flow<List<SocialMessage>> = messages
        override fun observeRoomMessages(roomId: String): Flow<List<SocialMessage>> = emptyFlow()
        override suspend fun getOlderMessages(
            conversationId: String,
            beforeCreatedAtMillis: Long,
            beforeMessageId: String
        ) = SocialMessagePage(
            messages = listOf(SocialMessage("older-message", "friend", "Older", 0)),
            nextCursorCreatedAtMillis = null,
            nextCursorMessageId = null
        )
        override suspend fun getOlderRoomMessages(
            roomId: String,
            beforeCreatedAtMillis: Long,
            beforeMessageId: String
        ) = SocialMessagePage(emptyList(), null, null)
        override suspend fun sendDirectMessage(conversationId: String, text: String, requestId: String) {
            sentText = text
        }
        override suspend fun sendRoomMessage(roomId: String, text: String, requestId: String) = Unit
        override suspend fun markConversationRead(conversationId: String) {
            markedReadConversation = conversationId
        }
        override suspend fun reportPlayer(
            reportedUid: String,
            reason: String,
            details: String,
            messageId: String?,
            requestId: String
        ) = Unit
        override suspend fun listNotifications(cursor: String?) =
            SocialListPage<SocialNotification>(emptyList(), null)
        override suspend fun markNotificationRead(notificationId: String) = Unit
        override suspend fun getNotificationPreferences() = SocialNotificationPreferences()
        override suspend fun setNotificationPreferences(preferences: SocialNotificationPreferences) = Unit
        override suspend fun registerNotificationToken(token: String, deviceId: String) = Unit
        override suspend fun removeNotificationToken(deviceId: String) = Unit
    }
}
