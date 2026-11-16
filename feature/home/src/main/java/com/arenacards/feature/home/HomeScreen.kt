package com.arenacards.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaCardColor
import com.arenacards.designsystem.ArenaCardTile
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
    onNotifications: () -> Unit,
    onDailyRewards: () -> Unit,
    onWallet: () -> Unit,
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
    isAuthenticated: Boolean,
    walletBalance: Int?,
    walletLoading: Boolean,
    playerLevel: Int?,
    experiencePoints: Long?,
    xpToNextLevel: Int?,
    dailyRewardCoins: Int?,
    dailyRewardAvailable: Boolean?,
    readyMissionCount: Int?
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
                .padding(horizontal = if (wideLayout) 32.dp else 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "ARENA  /  CARDS",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "A seat at every table.",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                CoinBalance(
                    amount = walletBalance?.toString() ?: "—",
                    label = when {
                        !isAuthenticated -> "Sign in for wallet"
                        walletBalance != null -> "Server wallet balance"
                        walletLoading -> "Wallet balance loading"
                        else -> "Wallet balance unavailable"
                    },
                    onClick = onWallet
                )
            }

            ArenaPanel(
                modifier = Modifier.fillMaxWidth(),
                backgroundBrush = Brush.linearGradient(
                    listOf(Color(0xFF162342), Color(0xFF252052), Color(0xFF182A3A))
                ),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlayerAvatar(
                            initials = playerAvatarId?.take(1) ?: "G",
                            name = playerName ?: "Guest player",
                            size = if (wideLayout) 64.dp else 54.dp,
                            backgroundColor = ArenaPalette.Purple,
                            avatarId = playerAvatarId
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isAuthenticated) "WELCOME BACK" else "WELCOME TO ARENACARDS",
                                color = Color(0xFFB7C7E7),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                playerName ?: "Guest player",
                                modifier = Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    playerUsername != null && playerGamerId != null ->
                                        "@$playerUsername  ·  $playerGamerId"
                                    playerUsername != null -> "@$playerUsername"
                                    isAuthenticated -> "Complete your player profile"
                                    else -> "Sign in to save your player identity"
                                },
                                modifier = Modifier.padding(top = 2.dp),
                                color = Color(0xFFCFD7E8),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    HomeProfileActions(
                        isAuthenticated = isAuthenticated,
                        onProfile = onProfile,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut,
                        compact = true
                    )
                }

                if (playerLevel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "LEVEL $playerLevel",
                            color = Color(0xFFFFD66B),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (experiencePoints != null) {
                            Text(
                                "$experiencePoints XP",
                                color = Color(0xFFDCE4F4),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    if (isAuthenticated) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onSignOut) {
                                Text("Sign out", color = Color(0xFFCFD7E8))
                            }
                        }
                    }
                    if (experiencePoints != null && xpToNextLevel != null) {
                        val xpIntoLevel = (1_000 - xpToNextLevel).coerceIn(0, 1_000)
                        LinearProgressIndicator(
                            progress = { xpIntoLevel / 1_000f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 7.dp)
                                .semantics {
                                    contentDescription =
                                        "Level $playerLevel progress: $xpIntoLevel of 1000 XP"
                                },
                            color = Color(0xFFFFC94A),
                            trackColor = Color.White.copy(alpha = 0.18f)
                        )
                        Text(
                            "$xpToNextLevel XP to the next level",
                            modifier = Modifier.padding(top = 4.dp),
                            color = Color(0xFFCFD7E8),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArenaCardTile(
                        color = ArenaCardColor.RED,
                        value = 4,
                        width = 34.dp,
                        height = 50.dp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    ArenaCardTile(
                        color = ArenaCardColor.YELLOW,
                        value = 6,
                        width = 34.dp,
                        height = 50.dp
                    )
                    ArenaCardTile(
                        color = ArenaCardColor.BLUE,
                        value = 8,
                        width = 34.dp,
                        height = 50.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "Your next move starts here.",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (isAuthenticated) "Your profile and progress sync securely."
                            else "Play practice or sign in to join online tables.",
                            color = Color(0xFFCFD7E8),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            ArenaPrimaryButton(
                text = "PLAY OFFLINE  ·  PRACTICE",
                onClick = onPlayOffline,
                modifier = Modifier.fillMaxWidth()
            )
            ArenaSecondaryButton(
                text = "Find a multiplayer table",
                onClick = onPlayOnline,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeActionCard(
                    title = "Friends",
                    subtitle = "Your crew and invites",
                    mark = "＋",
                    onClick = onFriends,
                    modifier = Modifier.weight(1f)
                )
                HomeActionCard(
                    title = "Rewards & missions",
                    subtitle = rewardSummary(
                        isAuthenticated,
                        dailyRewardCoins,
                        dailyRewardAvailable,
                        readyMissionCount
                    ),
                    mark = "✦",
                    onClick = onDailyRewards,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeActionCard(
                    title = "Competition",
                    subtitle = "Leaderboards & events",
                    mark = "↗",
                    onClick = onLeaderboard,
                    modifier = Modifier.weight(1f)
                )
                HomeActionCard(
                    title = "Settings",
                    subtitle = "Audio, motion & display",
                    mark = "⚙",
                    onClick = onSettings,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeActionCard(
                    title = "Player profile",
                    subtitle = "Your Arena identity",
                    mark = "◉",
                    onClick = onProfile,
                    modifier = Modifier.weight(1f)
                )
                HomeActionCard(
                    title = "Notifications",
                    subtitle = "Invites and Arena activity",
                    mark = "◌",
                    onClick = onNotifications,
                    modifier = Modifier.weight(1f)
                )
            }
            HomeActionCard(
                title = "Help & support",
                subtitle = "Support tools coming soon",
                mark = "?",
                onClick = onHelpSupport,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HomeProfileActions(
    isAuthenticated: Boolean,
    onProfile: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    compact: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End
    ) {
        ArenaSecondaryButton(
            text = if (isAuthenticated) "Profile" else "Sign in",
            onClick = if (isAuthenticated) onProfile else onSignIn
        )
        if (isAuthenticated && !compact) {
            ArenaSecondaryButton(text = "Sign out", onClick = onSignOut)
        }
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
    Card(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                mark,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun rewardSummary(
    isAuthenticated: Boolean,
    dailyRewardCoins: Int?,
    dailyRewardAvailable: Boolean?,
    readyMissionCount: Int?
): String = when {
    !isAuthenticated -> "Sign in to unlock rewards"
    readyMissionCount != null && readyMissionCount > 0 ->
        "$readyMissionCount mission reward${if (readyMissionCount == 1) "" else "s"} ready"
    dailyRewardAvailable == true && dailyRewardCoins != null ->
        "Claim $dailyRewardCoins coins today"
    dailyRewardAvailable == true -> "Daily reward ready to claim"
    dailyRewardAvailable == false -> "Daily rewards, missions & achievements"
    else -> "Daily rewards, missions & achievements"
}
