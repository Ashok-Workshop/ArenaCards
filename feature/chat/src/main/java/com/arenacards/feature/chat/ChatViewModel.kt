package com.arenacards.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arenacards.feature.friends.SocialConversation
import com.arenacards.feature.friends.SocialMessage
import com.arenacards.feature.friends.SocialRepository
import com.arenacards.feature.friends.SocialPlayer
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<SocialConversation> = emptyList(),
    val selectedConversationId: String? = null,
    val selectedRoomId: String? = null,
    val selectedFriend: SocialPlayer? = null,
    val messages: List<SocialMessage> = emptyList(),
    val draft: String = "",
    val olderCursorCreatedAtMillis: Long? = null,
    val olderCursorMessageId: String? = null,
    val hasOlderMessages: Boolean = true,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null
)

class ChatViewModel(
    private val repository: SocialRepository,
    private val initialFriendUid: String?,
    private val initialRoomId: String? = null
) : ViewModel() {
    val currentUid: String
        get() = repository.currentUid

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var messageJob: Job? = null
    private var olderMessages: List<SocialMessage> = emptyList()
    private var olderCursorCreatedAtMillis: Long? = null
    private var olderCursorMessageId: String? = null
    private var hasOlderMessages = true

    init {
        viewModelScope.launch {
            repository.observeConversations()
                .catch { error ->
                    if (error is CancellationException) throw error
                    showError(error)
                }
                .collect { conversations ->
                    _uiState.value = _uiState.value.copy(conversations = conversations)
                }
        }
        if (initialFriendUid != null) openFriend(initialFriendUid)
        if (initialRoomId != null) openRoom(initialRoomId)
    }

    fun openFriend(friendUid: String) = runRequest {
        val friend = repository.listFriends().items.firstOrNull { it.uid == friendUid }
            ?: SocialPlayer(friendUid, "Arena player", "", "")
        val conversationId = repository.openConversation(friendUid)
        openConversation(conversationId, friend)
    }

    fun selectConversation(conversation: SocialConversation) {
        openConversation(conversation.conversationId, conversation.friend)
    }

    fun updateDraft(value: String) {
        _uiState.value = _uiState.value.copy(draft = value.take(MAX_MESSAGE_LENGTH), errorMessage = null)
    }

    fun sendMessage() = runRequest {
        val state = _uiState.value
        val conversationId = state.selectedConversationId
        val roomId = state.selectedRoomId
        if (conversationId == null && roomId == null) {
            throw IllegalStateException("Select a conversation before sending a message.")
        }
        val text = state.draft.trim()
        if (text.isEmpty()) return@runRequest
        if (conversationId != null) {
            repository.sendDirectMessage(conversationId, text, UUID.randomUUID().toString())
            repository.markConversationRead(conversationId)
        } else {
            repository.sendRoomMessage(roomId!!, text, UUID.randomUUID().toString())
        }
        _uiState.value = _uiState.value.copy(draft = "", noticeMessage = null)
    }

    fun loadOlderMessages() = runRequest {
        val state = _uiState.value
        val conversationId = state.selectedConversationId
        val roomId = state.selectedRoomId
        val createdAt = olderCursorCreatedAtMillis ?: return@runRequest
        val messageId = olderCursorMessageId ?: return@runRequest
        val page = if (conversationId != null) {
            repository.getOlderMessages(conversationId, createdAt, messageId)
        } else if (roomId != null) {
            repository.getOlderRoomMessages(roomId, createdAt, messageId)
        } else {
            return@runRequest
        }
        val currentIds = state.messages.mapTo(mutableSetOf()) { it.messageId }
        olderMessages = (olderMessages + page.messages.asReversed())
            .distinctBy { it.messageId }
            .filterNot { it.messageId in currentIds }
        olderCursorCreatedAtMillis = page.nextCursorCreatedAtMillis
        olderCursorMessageId = page.nextCursorMessageId
        hasOlderMessages = page.nextCursorCreatedAtMillis != null
        publishMessages(
            liveMessages = state.messages.takeLast(MESSAGE_PAGE_SIZE)
        )
    }

    fun reportPlayer(
        reportedUid: String,
        reason: String,
        details: String,
        messageId: String? = null
    ) = runRequest {
        repository.reportPlayer(
            reportedUid = reportedUid,
            reason = reason,
            details = details,
            messageId = messageId,
            requestId = UUID.randomUUID().toString()
        )
        _uiState.value = _uiState.value.copy(noticeMessage = "Report submitted for moderator review.")
    }

    fun closeConversation() {
        messageJob?.cancel()
        messageJob = null
        olderMessages = emptyList()
        olderCursorCreatedAtMillis = null
        olderCursorMessageId = null
        hasOlderMessages = true
        _uiState.value = _uiState.value.copy(
            selectedConversationId = null,
            selectedRoomId = null,
            selectedFriend = null,
            messages = emptyList(),
            olderCursorCreatedAtMillis = null,
            olderCursorMessageId = null,
            hasOlderMessages = true,
            errorMessage = null
        )
    }

    private fun openConversation(conversationId: String, friend: SocialPlayer) {
        if (_uiState.value.selectedConversationId == conversationId) return
        messageJob?.cancel()
        olderMessages = emptyList()
        olderCursorCreatedAtMillis = null
        olderCursorMessageId = null
        hasOlderMessages = true
        _uiState.value = _uiState.value.copy(
            selectedConversationId = conversationId,
            selectedRoomId = null,
            selectedFriend = friend,
            messages = emptyList(),
            olderCursorCreatedAtMillis = null,
            olderCursorMessageId = null,
            hasOlderMessages = true,
            errorMessage = null
        )
        observeMessages(
            conversationId = conversationId,
            roomId = null,
            markRead = true
        )
    }

    private fun openRoom(roomId: String) {
        messageJob?.cancel()
        olderMessages = emptyList()
        olderCursorCreatedAtMillis = null
        olderCursorMessageId = null
        hasOlderMessages = true
        _uiState.value = _uiState.value.copy(
            selectedConversationId = null,
            selectedRoomId = roomId,
            selectedFriend = null,
            messages = emptyList(),
            olderCursorCreatedAtMillis = null,
            olderCursorMessageId = null,
            hasOlderMessages = true,
            errorMessage = null
        )
        observeMessages(
            conversationId = null,
            roomId = roomId,
            markRead = false
        )
    }

    private fun observeMessages(
        conversationId: String?,
        roomId: String?,
        markRead: Boolean
    ) {
        messageJob = viewModelScope.launch {
            val messageFlow = if (conversationId != null) {
                repository.observeMessages(conversationId)
            } else {
                repository.observeRoomMessages(checkNotNull(roomId))
            }
            messageFlow
                .catch { error ->
                    if (error is CancellationException) throw error
                    showError(error)
                }
                .collect { messages ->
                    val oldest = messages.firstOrNull()
                    if (olderCursorCreatedAtMillis == null) {
                        olderCursorCreatedAtMillis = oldest?.createdAtMillis
                        olderCursorMessageId = oldest?.messageId
                        hasOlderMessages = messages.size >= MESSAGE_PAGE_SIZE
                    }
                    publishMessages(
                        liveMessages = messages
                    )
                    if (markRead && conversationId != null) {
                        try {
                            repository.markConversationRead(conversationId)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            showError(error)
                        }
                    }
                }
        }
    }

    private fun publishMessages(
        liveMessages: List<SocialMessage>
    ) {
        _uiState.value = _uiState.value.copy(
            messages = (olderMessages + liveMessages)
                .distinctBy { it.messageId }
                .sortedBy { it.createdAtMillis ?: Long.MIN_VALUE },
            olderCursorCreatedAtMillis = olderCursorCreatedAtMillis,
            olderCursorMessageId = olderCursorMessageId,
            hasOlderMessages = hasOlderMessages
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
                showError(error)
            } finally {
                _uiState.value = _uiState.value.copy(isBusy = false)
            }
        }
    }

    private fun showError(error: Throwable) {
        _uiState.value = _uiState.value.copy(
            errorMessage = error.message ?: "Chat could not be updated."
        )
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 1_000
        const val MESSAGE_PAGE_SIZE = 50
    }
}

class ChatViewModelFactory(
    private val repository: SocialRepository,
    private val initialFriendUid: String?,
    private val initialRoomId: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ChatViewModel::class.java))
        return ChatViewModel(repository, initialFriendUid, initialRoomId) as T
    }
}
