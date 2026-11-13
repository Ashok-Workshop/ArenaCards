package com.arenacards.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arenacards.designsystem.ArenaCardColor
import com.arenacards.designsystem.ArenaCardTile
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton
import com.arenacards.designsystem.PlayerAvatar

@Composable
fun ArenaCardsGameScreen(
    playerCount: Int = 2,
    onExit: () -> Unit = {},
    viewModel: GameViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDifficultyDialog by remember { mutableStateOf(false) }
    LaunchedEffect(playerCount) {
        viewModel.ensurePlayerCount(playerCount)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val wideLayout = maxWidth >= 640.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wideLayout) 32.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PRACTICE TABLE",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Match the moment",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArenaSecondaryButton(
                        text = "CPU ${state.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        onClick = { showDifficultyDialog = true }
                    )
                    ArenaSecondaryButton(
                        text = if (state.isPaused) "Resume" else "Pause",
                        onClick = if (state.isPaused) viewModel::resumeGame else viewModel::pauseGame,
                        enabled = (state.winner == null && !state.isDraw) || state.isPaused
                    )
                    ArenaSecondaryButton(text = "Exit", onClick = onExit)
                }
            }

            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.status,
                    color = if (state.winner == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        ArenaPalette.Gold
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Active color: ${state.activeColor.name.lowercase()} • " +
                        if (state.turnDirection > 0) "clockwise" else "counter-clockwise",
                    modifier = Modifier.padding(top = 5.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.opponents.forEach { opponent ->
                    OpponentSeat(
                        opponent = opponent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ArenaPanel(
                modifier = Modifier.fillMaxWidth(),
                backgroundBrush = Brush.linearGradient(
                    listOf(
                        ArenaPalette.Purple.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.surface,
                        ArenaPalette.Blue.copy(alpha = 0.14f)
                    )
                )
            ) {
                Text(
                    text = "TABLE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp, 104.dp)
                                .background(
                                    Brush.linearGradient(listOf(ArenaPalette.Purple, ArenaPalette.Blue)),
                                    MaterialTheme.shapes.medium
                                )
                                .semantics { contentDescription = "Draw pile, ${state.drawPileCount} cards" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        }
                        Text(
                            text = "DRAW ${state.drawPileCount}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = "VS",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArenaCardTile(
                            color = state.topCard.color.toArenaColor(),
                            value = state.topCard.value,
                            width = 72.dp,
                            height = 104.dp
                        )
                        Text(
                            text = "TOP CARD • ${state.activeColor.name}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Your hand", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${state.playerCards.size} cards • hand score ${state.score}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = if (state.winner == null && !state.isDraw) "YOUR TURN" else "MATCH COMPLETE",
                    color = if (state.winner == null && !state.isDraw) ArenaPalette.Green else ArenaPalette.Gold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (state.playerCards.isEmpty()) {
                ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.winner ?: if (state.isDraw) "Round drawn." else "Your hand is empty.",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(138.dp)
                        .semantics { contentDescription = "Your cards" },
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    itemsIndexed(state.playerCards) { index, card ->
                        ArenaCardTile(
                            color = card.color.toArenaColor(),
                            value = card.value,
                            selected = state.selectedCardIndex == index,
                            enabled = index in state.playableCardIndices,
                            onClick = { viewModel.selectCard(index) },
                            width = if (wideLayout) 78.dp else 68.dp,
                            height = if (wideLayout) 116.dp else 104.dp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArenaSecondaryButton(
                    text = if (state.hasDrawnThisTurn) "Card drawn" else "Draw card",
                    onClick = viewModel::drawCard,
                    modifier = Modifier.weight(1f),
                    enabled = state.winner == null && !state.isDraw && !state.isPaused &&
                        state.currentPlayerIndex == 0 && !state.hasDrawnThisTurn
                )
                ArenaPrimaryButton(
                    text = "Play selected",
                    onClick = viewModel::playSelectedCard,
                    modifier = Modifier.weight(1f),
                    enabled = state.selectedCardIndex != null && state.winner == null &&
                        !state.isDraw && !state.isPaused
                )
            }

            if (state.hasDrawnThisTurn && state.winner == null && !state.isDraw) {
                ArenaSecondaryButton(
                    text = "End turn",
                    onClick = viewModel::endTurn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isPaused
                )
            }

            if (state.winner != null || state.isDraw) {
                ArenaPrimaryButton(
                    text = if (state.isDraw) "Replay draw" else "Play again",
                    onClick = { viewModel.startNewGame(playerCount) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(
                onClick = onExit,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Return to the lobby")
            }
        }
    }

    if (state.chooseWildColor) {
        WildColorDialog(
            onChoose = viewModel::chooseWildColor,
            onDismiss = viewModel::cancelWildColor
        )
    }

    if (state.isPaused) {
        AlertDialog(
            onDismissRequest = viewModel::resumeGame,
            title = { Text("Practice paused") },
            text = { Text("Your offline match is safely saved. Resume whenever you are ready.") },
            confirmButton = {
                TextButton(onClick = viewModel::resumeGame) { Text("Resume match") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showDifficultyDialog) {
        AlertDialog(
            onDismissRequest = { showDifficultyDialog = false },
            title = { Text("CPU difficulty") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CpuDifficulty.entries.forEach { difficulty ->
                        ArenaPrimaryButton(
                            text = difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
                            onClick = {
                                viewModel.setDifficulty(difficulty)
                                showDifficultyDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDifficultyDialog = false }) { Text("Close") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
private fun OpponentSeat(
    opponent: OpponentUiState,
    modifier: Modifier = Modifier
) {
    ArenaPanel(modifier = modifier) {
        PlayerAvatar(
            initials = opponent.name.substringAfterLast(" ").take(1),
            name = opponent.name,
            size = 42.dp,
            isActive = opponent.isActive,
            backgroundColor = ArenaPalette.Blue
        )
        Text(
            text = opponent.name,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = "${opponent.cardCount} cards",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun WildColorDialog(
    onChoose: (CardColor) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CardColor.RED, CardColor.BLUE, CardColor.GREEN, CardColor.YELLOW).forEach { color ->
                    ArenaPrimaryButton(
                        text = color.name.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = { onChoose(color) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

private fun CardColor.toArenaColor(): ArenaCardColor = when (this) {
    CardColor.RED -> ArenaCardColor.RED
    CardColor.BLUE -> ArenaCardColor.BLUE
    CardColor.GREEN -> ArenaCardColor.GREEN
    CardColor.YELLOW -> ArenaCardColor.YELLOW
    CardColor.WILD -> ArenaCardColor.WILD
}
