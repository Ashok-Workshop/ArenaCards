package com.arenacards.feature.game

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaSecondaryButton
import java.text.DateFormat
import java.util.Date

@Composable
fun PracticeHistoryScreen(
    repository: PracticeStatisticsRepository,
    onBack: () -> Unit
) {
    val statistics by repository.statistics.collectAsState(initial = PracticeStatistics())
    val achievements = earnedPracticeAchievements(statistics)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ArenaSecondaryButton(text = "‹  Back", onClick = onBack)
        Text("Practice history", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Private to this device. Practice results never grant competitive rewards.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPanel("Games", statistics.gamesPlayed, Modifier.weight(1f))
                StatPanel("Wins", statistics.wins, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPanel("Losses", statistics.losses, Modifier.weight(1f))
                StatPanel("Draws", statistics.draws, Modifier.weight(1f))
            }
        }

        ArenaPanel(modifier = Modifier.fillMaxWidth()) {
            Text("Achievements", style = MaterialTheme.typography.titleLarge)
            PracticeAchievement.entries.forEach { achievement ->
                val earned = achievement in achievements
                Text(
                    text = "${if (earned) "Earned" else "Locked"}  •  ${achievement.label()}",
                    modifier = Modifier.padding(top = 8.dp),
                    color = if (earned) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text("Recent matches", style = MaterialTheme.typography.titleLarge)
        if (statistics.recentMatches.isEmpty()) {
            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text("No practice matches recorded yet.")
                Text(
                    "Finish a match against the CPU to start your local history.",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                statistics.recentMatches.forEach { match ->
                    ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${match.result.name.lowercase().replaceFirstChar(Char::uppercase)} • " +
                                "${match.difficulty.name.lowercase().replaceFirstChar(Char::uppercase)} CPU",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(match.completedAtMillis))} • " +
                                "${match.durationMillis / 60_000} min • hand score ${match.finalHandScore}",
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPanel(label: String, value: Int, modifier: Modifier) {
    ArenaPanel(modifier = modifier) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun PracticeAchievement.label(): String = when (this) {
    PracticeAchievement.FIRST_MATCH -> "First practice match"
    PracticeAchievement.FIRST_WIN -> "First win"
    PracticeAchievement.TEN_MATCHES -> "Ten matches played"
    PracticeAchievement.HARD_WIN -> "Win against Hard CPU"
}
