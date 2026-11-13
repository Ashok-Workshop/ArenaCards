package com.arenacards.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton
import com.arenacards.designsystem.CoinBalance
import com.arenacards.designsystem.PlayerAvatar

@Composable
fun HomeScreen(
    onPlayOnline: () -> Unit,
    onPlayOffline: () -> Unit,
    onFriends: () -> Unit,
    onDailyRewards: () -> Unit,
    onLeaderboard: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onHelpSupport: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    playerName: String?,
    playerUsername: String?,
    playerGamerId: String?,
    playerAvatarId: String?,
    isAuthenticated: Boolean
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        val wideLayout = maxWidth >= 640.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wideLayout) 32.dp else 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ARENA  /  CARDS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Your table awaits.",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                CoinBalance(amount = "—", label = "Coin balance placeholder")
            }

            ArenaPanel(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PlayerAvatar(
                            initials = playerAvatarId?.take(1) ?: "G",
                            name = playerName ?: "Guest player",
                            size = if (wideLayout) 64.dp else 54.dp,
                            backgroundColor = ArenaPalette.Purple
                        )
                        Column {
                            Text(
                                text = playerName ?: "Guest Commander",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    playerUsername != null && playerGamerId != null ->
                                        "@$playerUsername  ·  $playerGamerId"
                                    playerUsername != null -> "@$playerUsername"
                                    isAuthenticated -> "Complete your player profile"
                                    else -> "Gamer ID assigned after profile setup"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArenaSecondaryButton(
                            text = if (isAuthenticated) "Profile" else "Sign in",
                            onClick = if (isAuthenticated) onProfile else onSignIn
                        )
                        if (isAuthenticated) {
                            ArenaSecondaryButton(text = "Sign out", onClick = onSignOut)
                        }
                    }
                }
                Text(
                    text = if (isAuthenticated) {
                        if (playerGamerId != null) {
                            "Firebase identity is active. Match statistics will update through trusted game services."
                        } else {
                            "Finish your player profile to receive a permanent Gamer ID."
                        }
                    } else {
                        "Sign in to create a synchronized player profile and permanent Gamer ID."
                    },
                    modifier = Modifier.padding(top = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "Choose your game",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameLaunchCard(
                    title = "Play Online",
                    subtitle = "Meet players at the table",
                    tag = "ONLINE",
                    onClick = onPlayOnline,
                    modifier = Modifier.weight(1f),
                    primary = true
                )
                GameLaunchCard(
                    title = "Play Offline",
                    subtitle = "Practice at your pace",
                    tag = "VS CPU",
                    onClick = onPlayOffline,
                    modifier = Modifier.weight(1f),
                    primary = false
                )
            }

            Text(
                text = "More in the arena",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (wideLayout) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeActionCard("Friends", "Invite your crew", "＋", onFriends, Modifier.weight(1f))
                    HomeActionCard("Daily rewards", "A new reason to return", "✦", onDailyRewards, Modifier.weight(1f))
                    HomeActionCard("Leaderboard", "See the standings", "↗", onLeaderboard, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeActionCard("Profile", "Your player card", "◉", onProfile, Modifier.weight(1f))
                    HomeActionCard("Settings", "Make it yours", "⚙", onSettings, Modifier.weight(1f))
                    HomeActionCard("Help & support", "Coming in a future update", "?", onHelpSupport, Modifier.weight(1f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeActionCard("Friends", "Invite your crew", "＋", onFriends, Modifier.weight(1f))
                    HomeActionCard("Daily rewards", "A new reason to return", "✦", onDailyRewards, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeActionCard("Leaderboard", "See the standings", "↗", onLeaderboard, Modifier.weight(1f))
                    HomeActionCard("Profile", "Your player card", "◉", onProfile, Modifier.weight(1f))
                }
                HomeActionCard("Settings", "Make it yours", "⚙", onSettings, Modifier.fillMaxWidth())
                HomeActionCard(
                    "Help & support",
                    "Coming in a future update",
                    "?",
                    onHelpSupport,
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GameLaunchCard(
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
    primary: Boolean
) {
    val launchBrush = if (primary) {
        Brush.linearGradient(
            listOf(
                ArenaPalette.Purple.copy(alpha = 0.3f),
                ArenaPalette.Blue.copy(alpha = 0.16f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(ArenaPalette.Green.copy(alpha = 0.18f), MaterialTheme.colorScheme.surface)
        )
    }
    ArenaPanel(modifier = modifier, backgroundBrush = launchBrush) {
        Text(
            text = tag,
            color = if (primary) MaterialTheme.colorScheme.secondary else ArenaPalette.Green,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        ArenaPrimaryButton(
            text = "Choose mode",
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    mark: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArenaPanel(modifier = modifier) {
        Text(
            text = mark,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        ArenaSecondaryButton(
            text = "Open",
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
