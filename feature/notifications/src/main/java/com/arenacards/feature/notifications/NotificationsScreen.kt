package com.arenacards.feature.notifications

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arenacards.designsystem.ArenaCardsLogoMark
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080E1D), Color(0xFF14233D), Color(0xFF080E1D))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                ArenaCardsLogoMark(Modifier.size(40.dp))
                Column {
                    Text("ARENA UPDATES", color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge)
                    Text("Notifications", style = MaterialTheme.typography.headlineMedium)
                }
            }
            ArenaSecondaryButton(text = "Back", onClick = onBack)
        }
        Text(
            "Push alerts are optional. Message contents are never included in push payloads.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ArenaPrimaryButton(
            text = "Enable device notifications",
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        )
        ArenaPanel(Modifier.fillMaxWidth().animateContentSize()) {
            Text("Notification preferences", style = MaterialTheme.typography.titleLarge)
            PreferenceToggle(
                title = "Friend requests",
                checked = state.preferences.friendRequests,
                enabled = !state.isBusy,
                onCheckedChange = { viewModel.setPreference("friendRequests", it) }
            )
            PreferenceToggle(
                title = "Room invitations",
                checked = state.preferences.roomInvitations,
                enabled = !state.isBusy,
                onCheckedChange = { viewModel.setPreference("roomInvitations", it) }
            )
            PreferenceToggle(
                title = "Messages",
                checked = state.preferences.messages,
                enabled = !state.isBusy,
                onCheckedChange = { viewModel.setPreference("messages", it) }
            )
            PreferenceToggle(
                title = "Match updates",
                checked = state.preferences.matchUpdates,
                enabled = !state.isBusy,
                onCheckedChange = { viewModel.setPreference("matchUpdates", it) }
            )
            PreferenceToggle(
                title = "Social updates",
                checked = state.preferences.socialUpdates,
                enabled = !state.isBusy,
                onCheckedChange = { viewModel.setPreference("socialUpdates", it) }
            )
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.noticeMessage?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        Text("Recent activity", style = MaterialTheme.typography.titleLarge)
        if (state.notifications.isEmpty()) {
            ArenaPanel(Modifier.fillMaxWidth().animateContentSize()) {
                Text("No notifications yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.notifications.forEach { notification ->
            ArenaPanel(Modifier.fillMaxWidth().animateContentSize()) {
                Text(
                    notification.type.toDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (notification.isRead) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.secondary
                )
                Text(
                    if (notification.isRead) "Read" else "New",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (!notification.isRead) {
                    ArenaSecondaryButton(
                        text = "Mark as read",
                        onClick = { viewModel.markRead(notification) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }
        }
        if (state.nextCursor != null) {
            ArenaSecondaryButton(
                text = "Load older notifications",
                onClick = viewModel::loadMore,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ArenaSecondaryButton(
            text = "Refresh",
            onClick = viewModel::refresh,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .semantics { contentDescription = title },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.padding(top = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun String.toDisplayName(): String = when (this) {
    "FRIEND_REQUEST" -> "New friend request"
    "FRIEND_REQUEST_ACCEPTED" -> "Friend request accepted"
    "ROOM_INVITATION" -> "Private room invitation"
    "PRIVATE_MESSAGE" -> "New direct message"
    "ROOM_MESSAGE" -> "New room message"
    else -> replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
}
