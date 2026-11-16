package com.arenacards.feature.friends

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arenacards.designsystem.ArenaCardsLogoMark
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton
import com.arenacards.designsystem.PlayerAvatar

@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMultiplayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Friends", "Requests", "Blocked", "Invites")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080E1D), Color(0xFF14233D), Color(0xFF080E1D))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Text("YOUR ARENA", color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge)
                    Text("Friends & invites", style = MaterialTheme.typography.headlineMedium)
                }
            }
            ArenaSecondaryButton(text = "Back", onClick = onBack)
        }

        ArenaPanel(Modifier.fillMaxWidth()) {
            Text("Find a player", style = MaterialTheme.typography.titleLarge)
            Text(
                "Search by the six-digit Gamer ID. Only public profiles appear in results.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = state.gamerId,
                onValueChange = viewModel::updateGamerId,
                label = { Text("Gamer ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            ArenaPrimaryButton(
                text = "Search",
                onClick = viewModel::searchPlayer,
                enabled = !state.isBusy && state.gamerId.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            state.searchResult?.let { player ->
                PlayerRow(player)
                ArenaSecondaryButton(
                    text = "Send friend request",
                    onClick = viewModel::sendFriendRequest,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            ArenaSecondaryButton(
                text = "Messages",
                onClick = onOpenMessages,
                modifier = Modifier.fillMaxWidth()
            )
            ArenaSecondaryButton(
                text = "Notifications & preferences",
                onClick = onOpenNotifications,
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.errorMessage?.let { MessagePanel(it, isError = true) }
        state.noticeMessage?.let { MessagePanel(it) }

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                if (state.friends.isEmpty()) EmptyPanel("Your friends will appear here.")
                state.friends.forEach { player ->
                    ArenaPanel(Modifier.fillMaxWidth()) {
                        PlayerRow(player)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArenaPrimaryButton(
                                text = "Message",
                                onClick = { onOpenChat(player.uid) },
                                modifier = Modifier.weight(1f)
                            )
                            ArenaSecondaryButton(
                                text = "Remove",
                                onClick = { viewModel.removeFriend(player) },
                                enabled = !state.isBusy,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ArenaSecondaryButton(
                            text = "Block player",
                            onClick = { viewModel.blockPlayer(player) },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
                if (state.friendsCursor != null) {
                    ArenaSecondaryButton(
                        text = "Load more friends",
                        onClick = viewModel::loadMoreFriends,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            1 -> {
                if (state.incomingRequests.isEmpty() && state.outgoingRequests.isEmpty()) {
                    EmptyPanel("No pending friend requests.")
                }
                state.incomingRequests.forEach { request ->
                    ArenaPanel(Modifier.fillMaxWidth()) {
                        PlayerRow(request.player)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArenaPrimaryButton(
                                text = "Accept",
                                onClick = { viewModel.acceptRequest(request) },
                                enabled = !state.isBusy,
                                modifier = Modifier.weight(1f)
                            )
                            ArenaSecondaryButton(
                                text = "Decline",
                                onClick = { viewModel.rejectRequest(request) },
                                enabled = !state.isBusy,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                state.outgoingRequests.forEach { request ->
                    ArenaPanel(Modifier.fillMaxWidth()) {
                        PlayerRow(request.player)
                        ArenaSecondaryButton(
                            text = "Cancel request",
                            onClick = { viewModel.cancelRequest(request) },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
                if (state.incomingCursor != null) {
                    ArenaSecondaryButton(
                        text = "Load more incoming requests",
                        onClick = viewModel::loadMoreIncomingRequests,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (state.outgoingCursor != null) {
                    ArenaSecondaryButton(
                        text = "Load more outgoing requests",
                        onClick = viewModel::loadMoreOutgoingRequests,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            2 -> {
                if (state.blockedPlayers.isEmpty()) EmptyPanel("No players are blocked.")
                state.blockedPlayers.forEach { player ->
                    ArenaPanel(Modifier.fillMaxWidth()) {
                        PlayerRow(player)
                        ArenaSecondaryButton(
                            text = "Unblock",
                            onClick = { viewModel.unblockPlayer(player) },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
                if (state.blockedCursor != null) {
                    ArenaSecondaryButton(
                        text = "Load more blocked players",
                        onClick = viewModel::loadMoreBlockedPlayers,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            else -> {
                if (state.invitations.isEmpty()) EmptyPanel("No room invitations right now.")
                state.invitations.forEach { invitation ->
                    ArenaPanel(Modifier.fillMaxWidth()) {
                        Text(
                            invitation.inviter?.displayName ?: "Arena player",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Private room code ${invitation.roomCode}",
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArenaPrimaryButton(
                                text = "Accept",
                                onClick = {
                                    viewModel.acceptInvitation(invitation, onOpenMultiplayer)
                                },
                                enabled = !state.isBusy,
                                modifier = Modifier.weight(1f)
                            )
                            ArenaSecondaryButton(
                                text = "Decline",
                                onClick = { viewModel.rejectInvitation(invitation) },
                                enabled = !state.isBusy,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (state.invitationsCursor != null) {
                        ArenaSecondaryButton(
                            text = "Load more invitations",
                            onClick = viewModel::loadMoreInvitations,
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        ArenaSecondaryButton(
            text = "Refresh social activity",
            onClick = viewModel::refresh,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlayerRow(player: SocialPlayer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlayerAvatar(
            initials = "",
            name = player.displayName,
            size = 44.dp,
            isActive = player.presence == "ONLINE",
            backgroundColor = ArenaPalette.Purple,
            avatarId = player.avatarId
        )
        Column {
            Text(player.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(player.gamerId.takeIf(String::isNotBlank), player.presence)
                    .joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EmptyPanel(text: String) {
    ArenaPanel(Modifier.fillMaxWidth()) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessagePanel(text: String, isError: Boolean = false) {
    ArenaPanel(Modifier.fillMaxWidth()) {
        Text(
            text,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
