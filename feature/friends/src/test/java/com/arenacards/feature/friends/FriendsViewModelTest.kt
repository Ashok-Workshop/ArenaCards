package com.arenacards.feature.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {
    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun GamerIdSearchAndFriendRequestUseRepositoryAndRefreshState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeSocialRepository()
        val viewModel = FriendsViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.friends.size)
        viewModel.updateGamerId("ar-222222")
        viewModel.searchPlayer()
        advanceUntilIdle()
        assertEquals("AR-222222", repository.lastSearchedGamerId)
        assertEquals("social-b", viewModel.uiState.value.searchResult?.uid)

        viewModel.sendFriendRequest()
        advanceUntilIdle()
        assertEquals(1, repository.sentRequestCount)
        assertNull(viewModel.uiState.value.searchResult)
        assertEquals("Friend request sent.", viewModel.uiState.value.noticeMessage)
        assertTrue(viewModel.uiState.value.errorMessage.isNullOrBlank())
    }

    @Test
    fun backendSearchFailureIsVisibleInUiState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeSocialRepository().apply { failSearch = true }
        val viewModel = FriendsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateGamerId("AR-222222")
        viewModel.searchPlayer()
        advanceUntilIdle()

        assertEquals("Player search is unavailable.", viewModel.uiState.value.errorMessage)
    }

    private class FakeSocialRepository : SocialRepository {
        override val currentUid = "social-a"
        var failSearch = false
        var lastSearchedGamerId: String? = null
        var sentRequestCount = 0
        private val player = SocialPlayer("social-b", "Player B", "player-b", "AR-222222")

        override suspend fun updatePresence(foreground: Boolean) = Unit
        override suspend fun searchPlayer(gamerId: String): SocialPlayer? {
            lastSearchedGamerId = gamerId
            if (failSearch) error("Player search is unavailable.")
            return player
        }
        override suspend fun sendFriendRequest(gamerId: String, requestId: String) {
            sentRequestCount += 1
        }
        override suspend fun listFriendRequests(direction: String, cursor: String?) =
            SocialListPage<FriendRequest>(emptyList(), null)
        override suspend fun acceptFriendRequest(request: FriendRequest) = Unit
        override suspend fun rejectFriendRequest(request: FriendRequest) = Unit
        override suspend fun cancelFriendRequest(request: FriendRequest) = Unit
        override suspend fun listFriends(cursor: String?) = SocialListPage(listOf(player), null)
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
        override fun observeConversations() = emptyFlow<List<SocialConversation>>()
        override suspend fun openConversation(friendUid: String) = "conversation-id"
        override fun observeMessages(conversationId: String) = emptyFlow<List<SocialMessage>>()
        override fun observeRoomMessages(roomId: String) = emptyFlow<List<SocialMessage>>()
        override suspend fun getOlderMessages(
            conversationId: String,
            beforeCreatedAtMillis: Long,
            beforeMessageId: String
        ) = SocialMessagePage(emptyList(), null, null)
        override suspend fun getOlderRoomMessages(
            roomId: String,
            beforeCreatedAtMillis: Long,
            beforeMessageId: String
        ) = SocialMessagePage(emptyList(), null, null)
        override suspend fun sendDirectMessage(conversationId: String, text: String, requestId: String) = Unit
        override suspend fun sendRoomMessage(roomId: String, text: String, requestId: String) = Unit
        override suspend fun markConversationRead(conversationId: String) = Unit
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
