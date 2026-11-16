package com.arenacards.feature.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WalletScreen(
    isAuthenticated: Boolean,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: WalletViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ArenaSecondaryButton(text = "‹  Back", onClick = onBack)
        Text("Arena account", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Wallet, rewards, missions, and server-verified progression.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        if (!isAuthenticated) {
            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text("Sign in to access your server wallet.")
                ArenaPrimaryButton(
                    text = "Sign in",
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
            return@Column
        }

        state.errorMessage?.let { message ->
            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.liveRegionAnnouncement()
                )
                ArenaSecondaryButton(
                    text = "Retry",
                    onClick = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
        state.successMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.fillMaxWidth().liveRegionAnnouncement(),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val account = state.account
        if (state.isLoading && account == null) {
            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text("Loading secure account data…")
            }
        }
        account?.let { snapshot ->
            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text("WALLET BALANCE", style = MaterialTheme.typography.labelLarge)
                Text(
                    snapshot.wallet.coinsBalance.toString(),
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Earned ${snapshot.wallet.totalCoinsEarned}  •  Spent ${snapshot.wallet.totalCoinsSpent}",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text("Daily reward", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (snapshot.dailyReward.streakDays > 0) {
                        "${snapshot.dailyReward.streakDays}-day streak • resets daily at 00:00 UTC"
                    } else {
                        "Daily claim resets at 00:00 UTC"
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (snapshot.dailyReward.claimAvailable) {
                        "Today's server reward: ${snapshot.dailyReward.nextRewardCoins} coins"
                    } else {
                        "Next claim: ${formatTimestamp(snapshot.dailyReward.nextClaimAtMillis)}"
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                ArenaPrimaryButton(
                    text = if (state.isClaiming) "Processing…" else "Claim daily reward",
                    onClick = viewModel::claimDailyReward,
                    enabled = snapshot.dailyReward.claimAvailable && !state.isClaiming,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }

            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text("Daily missions", style = MaterialTheme.typography.titleLarge)
                snapshot.missions.forEach { mission ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                mission.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (mission.claimed) "Claimed" else "${mission.rewardAmount} coins",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Text(
                            mission.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = {
                                (mission.currentProgress.toFloat() / mission.targetValue)
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semanticsProgress(mission)
                        )
                        Text(
                            "Progress ${mission.currentProgress}/${mission.targetValue} • " +
                                "expires ${formatTimestamp(mission.expiryTimeMillis)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        when {
                            mission.claimed -> Text(
                                "Reward claimed",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            mission.completed -> ArenaSecondaryButton(
                                text = if (state.isClaiming) "Processing…" else {
                                    "Claim ${mission.rewardXp} XP mission reward"
                                },
                                onClick = { viewModel.claimMission(mission.id) },
                                enabled = !state.isClaiming,
                                modifier = Modifier.fillMaxWidth()
                            )
                            else -> Text(
                                "Complete the server-verified activity to unlock this reward.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Text(
                    "Offline practice does not earn competitive coins or server mission progress.",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text("Progression", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Level ${snapshot.progression.level} • " +
                        "${snapshot.progression.experiencePoints} XP",
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                val xpIntoLevel = snapshot.progression.experiencePoints -
                    (snapshot.progression.level - 1) * XP_PER_LEVEL
                LinearProgressIndicator(
                    progress = { (xpIntoLevel.toFloat() / XP_PER_LEVEL).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    "${snapshot.progression.xpToNextLevel} XP to level ${snapshot.progression.level + 1}",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Achievements",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                snapshot.achievements.forEach { achievement ->
                    Text(
                        "${if (achievement.unlocked) "Unlocked" else "In progress"} • " +
                            "${achievement.title} (${achievement.progress}/${achievement.targetValue})",
                        modifier = Modifier.padding(top = 6.dp),
                        color = if (achievement.unlocked) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        achievement.description,
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "Unlock history",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleSmall
                )
                if (snapshot.achievementUnlockHistory.isEmpty()) {
                    Text(
                        "No server achievements unlocked yet.",
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    snapshot.achievementUnlockHistory.forEach { unlock ->
                        Text(
                            "${unlock.title} • ${formatTimestamp(unlock.unlockedAtMillis)}",
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        ArenaPanel(modifier = Modifier.fillMaxWidth()) {
            Text("Coin transaction history", style = MaterialTheme.typography.titleLarge)
            when {
                account == null && state.isLoading -> Text(
                    "Loading transaction history…",
                    modifier = Modifier.padding(top = 8.dp)
                )
                state.transactions.isEmpty() -> Text(
                    "No coin transactions yet.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> state.transactions.forEach { transaction ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "${transaction.type.replace('_', ' ')} • " +
                                "${if (transaction.amount > 0) "+" else ""}${transaction.amount}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${transaction.source} • ${formatTimestamp(transaction.timestampMillis)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            state.transactionError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            if (state.hasMoreTransactions || state.isLoadingMore) {
                ArenaSecondaryButton(
                    text = if (state.isLoadingMore) "Loading…" else "Load older transactions",
                    onClick = viewModel::loadMoreTransactions,
                    enabled = !state.isLoadingMore,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
        }
    }
}

private fun Modifier.liveRegionAnnouncement(): Modifier =
    this.then(
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        }
    )

private fun Modifier.semanticsProgress(mission: ArenaMission): Modifier =
    this.then(
        Modifier.semantics {
            contentDescription = "${mission.title}: ${mission.currentProgress} " +
                "of ${mission.targetValue}"
        }
    )

private fun formatTimestamp(value: Long?): String =
    value?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .format(Date(it))
    } ?: "—"

private const val XP_PER_LEVEL = 1_000
