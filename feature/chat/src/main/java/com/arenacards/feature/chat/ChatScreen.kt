package com.arenacards.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arenacards.designsystem.ArenaCardsLogoMark
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showReportDialog by remember { mutableStateOf(false) }
    var reportDetails by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("SPAM") }
    var reportMessageId by remember { mutableStateOf<String?>(null) }
    var reportTargetUid by remember { mutableStateOf<String?>(null) }
    val selectedFriend = state.selectedFriend
    val inConversation = state.selectedConversationId != null || state.selectedRoomId != null

    Column(
        modifier = modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080E1D), Color(0xFF13223A), Color(0xFF080E1D))))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArenaCardsLogoMark(Modifier.size(40.dp))
                Column {
                    Text("ARENA CHAT", color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge)
                    Text(
                        selectedFriend?.displayName ?: if (state.selectedRoomId != null) {
                            "Private room chat"
                        } else {
                            "Messages"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            if (!inConversation) {
                ArenaSecondaryButton(text = "Back", onClick = onBack)
            } else {
                ArenaSecondaryButton(
                    text = if (state.selectedRoomId != null) "Back" else "Inbox",
                    onClick = if (state.selectedRoomId != null) onBack else viewModel::closeConversation
                )
            }
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        state.noticeMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
        }

        if (!inConversation) {
            if (state.conversations.isEmpty()) {
                ArenaPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "No conversations yet. Open a friend's profile and choose Message.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.conversations, key = { it.conversationId }) { conversation ->
                    ArenaPanel(
                        Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Text(
                            conversation.friend.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            conversation.lastMessagePreview.ifBlank { "Start a conversation" },
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (conversation.unreadCount > 0) {
                            Text(
                                "${conversation.unreadCount} unread",
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        ArenaPrimaryButton(
                            text = "Open conversation",
                            onClick = { viewModel.selectConversation(conversation) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            if (selectedFriend != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArenaSecondaryButton(
                        text = "Report player",
                        onClick = {
                            reportMessageId = null
                            reportTargetUid = selectedFriend.uid
                            showReportDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (state.hasOlderMessages) {
                ArenaSecondaryButton(
                    text = "Load older messages",
                    onClick = viewModel::loadOlderMessages,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = false,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.messageId }) { message ->
                    val ownMessage = message.senderUid == viewModel.currentUid
                    ArenaPanel(
                        modifier = Modifier
                            .fillMaxWidth(if (ownMessage) 0.88f else 0.96f)
                            .align(if (ownMessage) Alignment.End else Alignment.Start)
                            .animateContentSize(),
                        backgroundBrush = if (ownMessage) {
                            Brush.linearGradient(listOf(Color(0xFF174F53), Color(0xFF203D58)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF202D47), Color(0xFF18243A)))
                        },
                        contentColor = Color.White
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (ownMessage) "YOU" else selectedFriend?.displayName ?: "ROOM PLAYER",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ownMessage) Color(0xFF78E2C1) else Color(0xFF8DCBF0)
                            )
                            if (ownMessage) {
                                Text("SENT", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.48f))
                            }
                        }
                        Text(
                            message.text,
                            modifier = Modifier.padding(top = 7.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF1F5FC)
                        )
                        if (!ownMessage) {
                            TextButton(
                                onClick = {
                                    reportMessageId = message.messageId.takeIf { selectedFriend != null }
                                    reportTargetUid = selectedFriend?.uid ?: message.senderUid
                                    showReportDialog = true
                                }
                            ) {
                                Text("Report message")
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::updateDraft,
                    label = { Text("Message") },
                    supportingText = { Text("${state.draft.length}/1000") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                ArenaPrimaryButton(
                    text = "Send",
                    onClick = viewModel::sendMessage,
                    enabled = !state.isBusy && state.draft.isNotBlank(),
                    modifier = Modifier
                )
            }
        }
    }

    if (showReportDialog && reportTargetUid != null) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report ${selectedFriend?.displayName ?: "room player"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose the closest reason. Reports are private and reviewed by moderators.")
                    listOf("SPAM", "HARASSMENT", "ABUSIVE_CONTENT", "INAPPROPRIATE", "OTHER")
                        .forEach { reason ->
                            TextButton(onClick = { reportReason = reason }) {
                                Text(
                                    if (reportReason == reason) "● ${reason.replace('_', ' ')}"
                                    else reason.replace('_', ' ')
                                )
                            }
                        }
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it.take(500) },
                        label = { Text("Details (optional)") },
                        supportingText = { Text("${reportDetails.length}/500") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isBusy,
                    onClick = {
                        val targetUid = reportTargetUid
                        if (targetUid != null) {
                            viewModel.reportPlayer(targetUid, reportReason, reportDetails, reportMessageId)
                        }
                        showReportDialog = false
                        reportDetails = ""
                        reportMessageId = null
                        reportTargetUid = null
                    }
                ) { Text("Submit report") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }
}
