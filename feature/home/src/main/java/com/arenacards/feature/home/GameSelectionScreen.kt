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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaInfoDialog
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton

@Composable
fun GameSelectionScreen(
    online: Boolean,
    onBack: () -> Unit,
    onStartOfflineGame: (playerCount: Int) -> Unit
) {
    var playerCount by remember { mutableIntStateOf(2) }
    var showOnlineNotice by remember { mutableStateOf(false) }
    val title = if (online) "Play online" else "Play offline"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        val wideLayout = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wideLayout) 32.dp else 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ArenaSecondaryButton(text = "‹  Back", onClick = onBack)
            Text(
                text = if (online) "MULTIPLAYER PREVIEW" else "PRACTICE TABLE",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge
            )
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(
                text = if (online) {
                    "Choose your table size. Online play is displayed as a preview until a trusted multiplayer service is implemented."
                } else {
                    "Set up a practice table with two to four players. Other seats are local CPU placeholders for this UI phase."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )

            ArenaPanel(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Players at the table",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (online) "Includes you and other players" else "You plus CPU seats",
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    (2..4).forEach { count ->
                        PlayerCountOption(
                            count = count,
                            selected = count == playerCount,
                            modifier = Modifier.weight(1f),
                            onClick = { playerCount = count }
                        )
                    }
                }
            }

            ArenaPanel(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (online) "Online play is not connected" else "Practice, with clear limits",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (online) {
                        "This build does not connect players, create rooms, or award competitive rewards. The online button below explains the current status instead of pretending a match can start."
                    } else {
                        "This is a local presentation built around the existing deck, dealing, matching, and score helpers. Competitive online rewards are not available in offline games."
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ArenaPrimaryButton(
                text = if (online) "Online service unavailable" else "Start practice match",
                onClick = {
                    if (online) {
                        showOnlineNotice = true
                    } else {
                        onStartOfflineGame(playerCount)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showOnlineNotice) {
        ArenaInfoDialog(
            title = "Online play is not available yet",
            message = "ArenaCards has no multiplayer backend in this phase. No room was created and no players were contacted.",
            onDismiss = { showOnlineNotice = false }
        )
    }
}

@Composable
private fun PlayerCountOption(
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val buttonModifier = modifier.semantics {
        contentDescription = "$count players"
        this.selected = selected
    }
    if (selected) {
        ArenaPrimaryButton(
            text = "$count",
            onClick = onClick,
            modifier = buttonModifier
        )
    } else {
        ArenaSecondaryButton(
            text = "$count",
            onClick = onClick,
            modifier = buttonModifier
        )
    }
}
