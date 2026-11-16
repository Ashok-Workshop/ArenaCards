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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton

@Composable
fun GameSelectionScreen(
    online: Boolean,
    onBack: () -> Unit,
    onStartOfflineGame: (playerCount: Int) -> Unit,
    onStartOnlineGame: () -> Unit
) {
    var playerCount by remember { mutableIntStateOf(2) }
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
                text = if (online) "REAL-PLAYER MULTIPLAYER" else "OFFLINE PRACTICE",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (online) {
                    "Join real-player matchmaking or create a private room. Match actions and results are confirmed by ArenaCards servers."
                } else {
                    "A relaxed table for learning the game. Choose your table size and play against local CPU opponents."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )

            if (online) {
                ArenaPanel(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundBrush = Brush.linearGradient(
                        listOf(ArenaPalette.Navy, Color(0xFF252052), Color(0xFF182A3A))
                    ),
                    contentColor = Color.White
                ) {
                    Text("Your online table", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Choose player count, enter a private room, or invite friends in the multiplayer lobby.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFFCFD7E8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ArenaPrimaryButton(
                        text = "Open multiplayer",
                        onClick = onStartOnlineGame,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                }
            } else {
                ArenaPanel(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundBrush = Brush.linearGradient(
                        listOf(ArenaPalette.Navy, Color(0xFF252052), Color(0xFF182A3A))
                    ),
                    contentColor = Color.White
                ) {
                    Text("Players at the table", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "You + ${playerCount - 1} CPU ${if (playerCount == 2) "opponent" else "opponents"}",
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        color = Color(0xFFCFD7E8),
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
                    Text(
                        "Practice is local. It does not award competitive coins, rank, or online mission progress.",
                        modifier = Modifier.padding(top = 14.dp),
                        color = Color(0xFFCFD7E8),
                        style = MaterialTheme.typography.bodySmall
                    )
                    ArenaPrimaryButton(
                        text = "Start practice match",
                        onClick = { onStartOfflineGame(playerCount) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                }
            }
        }
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
