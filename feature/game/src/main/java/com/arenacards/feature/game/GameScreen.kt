package com.arenacards.feature.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arenacards.designsystem.ArenaCardsLogoMark
import com.arenacards.designsystem.ArenaCardColor
import com.arenacards.designsystem.ArenaCardBack
import com.arenacards.designsystem.ArenaCardReveal
import com.arenacards.designsystem.ArenaCardTile
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton
import com.arenacards.designsystem.ArenaTableBackdrop
import com.arenacards.designsystem.PlayerAvatar
import com.arenacards.feature.settings.ArenaSettings

@Composable
fun ArenaCardsGameScreen(
    playerCount: Int = 2,
    onExit: () -> Unit = {},
    onReturnHome: () -> Unit = onExit,
    onOpenSettings: () -> Unit = {},
    statisticsRepository: PracticeStatisticsRepository = NoPracticeStatisticsRepository,
    settings: ArenaSettings = ArenaSettings(),
    onFeedback: (GameFeedback) -> Unit = {},
    viewModel: GameViewModel = viewModel(
        factory = remember(statisticsRepository) {
            GameViewModelFactory(statisticsRepository)
        }
    )
) {
    val state by viewModel.uiState.collectAsState()
    var showPauseMenu by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showRestartConfirmation by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var pausedForExit by remember { mutableStateOf(false) }
    var discardBounds by remember { mutableStateOf<Rect?>(null) }
    var isDraggingCard by remember { mutableStateOf(false) }
    val isMatchOver = state.winner != null || state.isDraw
    val canDraw = !isMatchOver && !state.isPaused && state.currentPlayerIndex == 0 &&
        !state.hasDrawnThisTurn && !state.wildDrawFourChallengeAvailable &&
        state.pendingUnoCallDeadlineMillis == null

    fun requestExit() {
        showPauseMenu = false
        pausedForExit = !state.isPaused && !isMatchOver
        if (pausedForExit) viewModel.pauseGame()
        showExitConfirmation = true
    }

    fun cancelExit() {
        showExitConfirmation = false
        if (pausedForExit) viewModel.resumeGame()
        else if (state.isPaused) showPauseMenu = true
        pausedForExit = false
    }

    fun playCard(index: Int) {
        viewModel.selectCard(index)
        viewModel.playSelectedCard()
    }

    BackHandler(onBack = ::requestExit)

    LaunchedEffect(playerCount) {
        viewModel.ensurePlayerCount(playerCount)
    }
    LaunchedEffect(state.feedbackSequence) {
        state.feedbackEvent?.let(onFeedback)
    }
    LaunchedEffect(state.pendingUnoCallDeadlineMillis, state.isPaused) {
        val deadline = state.pendingUnoCallDeadlineMillis
        if (deadline != null && !state.isPaused) {
            kotlinx.coroutines.delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
            viewModel.catchMissedUno()
        }
    }

    ArenaTableBackdrop(Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 640.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wideLayout) 32.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PracticeHeader(
                wideLayout = wideLayout,
                isPaused = state.isPaused,
                isMatchOver = isMatchOver,
                onPause = {
                    if (state.isPaused) {
                        showPauseMenu = false
                        viewModel.resumeGame()
                    } else {
                        viewModel.pauseGame()
                        showPauseMenu = true
                    }
                },
                onExit = ::requestExit
            )

            ArenaPanel(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                backgroundBrush = Brush.linearGradient(
                    listOf(Color(0xFF202B47), Color(0xFF263B4B))
                ),
                contentColor = Color.White
            ) {
                Text(
                    text = state.status,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    color = if (state.winner == null && !state.isDraw) {
                        Color.White
                    } else {
                        Color(0xFFFFD66B)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Active color: ${state.activeColor.name.lowercase()} • " +
                        if (state.turnDirection > 0) "clockwise" else "counter-clockwise",
                    modifier = Modifier.padding(top = 5.dp),
                    color = Color(0xFFCFD7E8),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (state.currentPlayerIndex == 0) "YOUR TURN"
                    else "CPU ${state.currentPlayerIndex} IS PLAYING",
                    modifier = Modifier.padding(top = 8.dp).semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    color = if (state.currentPlayerIndex == 0) Color(0xFFFFD66B)
                    else Color(0xFFCFD7E8),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.canAnnounceUno && !state.isPaused) {
                ArenaPanel(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundBrush = Brush.linearGradient(
                        listOf(Color(0xFF493A19), Color(0xFF2D2940))
                    ),
                    contentColor = Color.White
                ) {
                    Text(
                        "One card left",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Announce UNO within 10 seconds or a CPU opponent may catch the missed call.",
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color(0xFFDCE4F4),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ArenaPrimaryButton(
                        text = "UNO!",
                        onClick = viewModel::announceUno,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }
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
                    listOf(Color(0xFF113B36), Color(0xFF155044), Color(0xFF103C3A))
                ),
                contentColor = Color.White
            ) {
                Text(
                    text = "THE TABLE",
                    color = Color(0xFFB7D9CE),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canDraw && state.playableCardIndices.isEmpty()) {
                            Text("↓ DRAW", color = Color(0xFFFFD66B), fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .border(
                                    width = if (canDraw && state.playableCardIndices.isEmpty()) 3.dp else 1.dp,
                                    color = if (canDraw && state.playableCardIndices.isEmpty()) {
                                        Color(0xFFFFD66B)
                                    } else {
                                        Color.White.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(enabled = canDraw, onClick = viewModel::drawCard)
                                .padding(5.dp)
                                .semantics {
                                    contentDescription = if (canDraw) {
                                        "Draw a card, ${state.drawPileCount} cards remain"
                                    } else {
                                        "Draw pile, ${state.drawPileCount} cards"
                                    }
                                }
                        ) {
                            ArenaCardBack(width = 72.dp, height = 104.dp)
                        }
                        Text(
                            text = "DRAW ${state.drawPileCount}",
                            color = Color(0xFFCFD7E8),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { discardBounds = it.boundsInRoot() }
                                .border(
                                    width = if (isDraggingCard) 3.dp else 1.dp,
                                    color = if (isDraggingCard) Color(0xFFFFD66B)
                                    else Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(5.dp)
                        ) {
                            ArenaCardReveal(
                                color = state.topCard.color.toArenaColor(),
                                value = state.topCard.value,
                                eventId = state.topCard.id,
                                width = 72.dp,
                                height = 104.dp,
                                reducedMotion = settings.reducedMotionEnabled
                            )
                        }
                        Text(
                            text = "TOP CARD • ${state.activeColor.name}",
                            color = Color(0xFFCFD7E8),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    text = when {
                        isDraggingCard -> "Release on the glowing discard pile to play."
                        state.currentPlayerIndex != 0 -> "The table is moving. Watch the active seat."
                        state.wildDrawFourChallengeAvailable ->
                            "Respond to the Wild Draw Four challenge to continue."
                        state.playableCardIndices.isEmpty() && !state.hasDrawnThisTurn ->
                            "No playable cards. Draw one card from the deck."
                        else -> "Drag a playable card to the discard pile, or tap a playable card."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = Color(0xFFE0E8F0),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Your hand", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${state.playerCards.size} cards  •  ${state.playableCardIndices.size} playable",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = if (state.currentPlayerIndex == 0 && !isMatchOver) "YOUR SEAT"
                    else if (isMatchOver) "FINAL HAND" else "TABLE ACTIVE",
                    color = if (state.currentPlayerIndex == 0 && !isMatchOver) ArenaPalette.Green
                    else ArenaPalette.Gold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (state.playerCards.isEmpty()) {
                Text(
                    text = if (state.isDraw) "Practice round complete" else "Final hand",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (wideLayout) 150.dp else 136.dp)
                        .semantics { contentDescription = "Your cards" },
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    itemsIndexed(
                        items = state.playerCards,
                        key = { index, card -> card.id.ifBlank { "legacy-hand-$index" } }
                    ) { index, card ->
                        ArenaCardTile(
                            color = card.color.toArenaColor(),
                            value = card.value,
                            selected = state.selectedCardIndex == index,
                            enabled = index in state.playableCardIndices,
                            onClick = { playCard(index) },
                            width = if (wideLayout) 78.dp else 68.dp,
                            height = if (wideLayout) 116.dp else 104.dp,
                            reducedMotion = settings.reducedMotionEnabled,
                            dropTargetBounds = discardBounds,
                            onDrop = { playCard(index) },
                            onDragStateChanged = { isDraggingCard = it }
                        )
                    }
                }
            }

            ArenaSecondaryButton(
                text = if (state.hasDrawnThisTurn) "Card drawn" else "Draw a card",
                onClick = viewModel::drawCard,
                modifier = Modifier.fillMaxWidth(),
                enabled = canDraw
            )

            if (state.hasDrawnThisTurn && state.winner == null && !state.isDraw) {
                ArenaSecondaryButton(
                    text = "End turn",
                    onClick = viewModel::endTurn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isPaused && state.pendingUnoCallDeadlineMillis == null
                )
            }

            if (isMatchOver) {
                AnimatedVisibility(
                    visible = true,
                    enter = if (settings.reducedMotionEnabled) fadeIn(tween(0))
                    else fadeIn(tween(260)) + scaleIn(initialScale = 0.92f, animationSpec = tween(260))
                ) {
                    ArenaPanel(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundBrush = Brush.linearGradient(
                            listOf(Color(0xFF312A4F), Color(0xFF24374A), Color(0xFF193A38))
                        ),
                        contentColor = Color.White
                    ) {
                        Text(
                            when {
                                state.isDraw -> "ROUND DRAW"
                                state.winner?.startsWith("You") == true -> "ARENA VICTORY"
                                else -> "MATCH COMPLETE"
                            },
                            color = Color(0xFFFFD66B),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.winner ?: "The practice round ended in a draw.",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Hand score ${state.score}  •  ${formatMatchDuration(state.matchDurationMillis)}  •  CPU opponents",
                            modifier = Modifier.padding(top = 8.dp),
                            color = Color(0xFFCFD7E8),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Offline practice does not change competitive rank or wallet rewards.",
                            modifier = Modifier.padding(top = 8.dp),
                            color = Color(0xFFCFD7E8),
                            style = MaterialTheme.typography.bodySmall
                        )
                        ArenaPrimaryButton(
                            text = "Play again",
                            onClick = { viewModel.startNewGame(playerCount) },
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                        )
                        ArenaSecondaryButton(
                            text = "Return to home",
                            onClick = onReturnHome,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
                state.statisticsSaveError?.let { saveError ->
                    Text(
                        saveError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Assertive
                        }
                    )
                }
            }
            TextButton(
                onClick = ::requestExit,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Leave practice")
            }
        }
    }
    }

    if (state.chooseWildColor) {
        WildColorDialog(
            onChoose = viewModel::chooseWildColor,
            onDismiss = viewModel::cancelWildColor
        )
    }

    if (state.wildDrawFourChallengeAvailable) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Challenge Wild Draw Four?") },
            text = {
                Text(
                    "Challenge if you believe the player had a card matching the active color. " +
                        "A failed challenge makes you draw six cards."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.respondToWildDrawFourChallenge(true) }) {
                    Text("Challenge")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondToWildDrawFourChallenge(false) }) {
                    Text("Draw four")
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showPauseMenu) {
        AlertDialog(
            onDismissRequest = {
                showPauseMenu = false
                viewModel.resumeGame()
            },
            title = { Text("Practice paused") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The CPU is paused. Your current match stays on this screen until you leave.")
                    ArenaSecondaryButton(
                        text = "Restart this match",
                        onClick = { showRestartConfirmation = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ArenaSecondaryButton(
                        text = "Game rules",
                        onClick = {
                            showPauseMenu = false
                            showRules = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ArenaSecondaryButton(
                        text = "Settings",
                        onClick = {
                            showPauseMenu = false
                            onOpenSettings()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ArenaSecondaryButton(
                        text = "Leave practice",
                        onClick = ::requestExit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPauseMenu = false
                    viewModel.resumeGame()
                }) { Text("Resume match") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = ::cancelExit,
            title = { Text("Leave this practice match?") },
            text = {
                Text(
                    "Leaving ends this local match. It does not award competitive coins or ranking."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onExit()
                }) { Text("Leave match") }
            },
            dismissButton = {
                TextButton(onClick = ::cancelExit) { Text("Stay here") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showRestartConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestartConfirmation = false },
            title = { Text("Start a new practice match?") },
            text = { Text("The current hand and its result will be discarded.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirmation = false
                    showPauseMenu = false
                    viewModel.startNewGame(playerCount)
                }) { Text("Restart match") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirmation = false }) { Text("Cancel") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("Practice rules") },
            text = {
                Text(
                    "Play a card that matches the active color or value. Special cards change the turn, " +
                        "draw wild cards let you choose a color, and Wild Draw Four challenges follow " +
                        "the current match rules."
                )
            },
            confirmButton = {
                TextButton(onClick = { showRules = false }) { Text("Got it") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

}

@Composable
private fun PracticeHeader(
    wideLayout: Boolean,
    isPaused: Boolean,
    isMatchOver: Boolean,
    onPause: () -> Unit,
    onExit: () -> Unit
) {
    val title: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArenaCardsLogoMark(Modifier.size(42.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "ARENA • PRACTICE TABLE",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge
                )
                Text("Match the moment", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
    val controls: @Composable RowScope.() -> Unit = {
        ArenaSecondaryButton(
            text = if (isPaused) "Resume" else "Pause",
            onClick = onPause,
            enabled = !isMatchOver || isPaused,
            modifier = if (wideLayout) Modifier else Modifier.weight(1f)
        )
        ArenaSecondaryButton(
            text = "Exit",
            onClick = onExit,
            modifier = if (wideLayout) Modifier else Modifier.weight(1f)
        )
    }
    if (wideLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(content = title)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = controls)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(content = title)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = controls)
        }
    }
}

@Composable
private fun OpponentSeat(
    opponent: OpponentUiState,
    modifier: Modifier = Modifier
) {
    ArenaPanel(modifier = modifier.animateContentSize()) {
        PlayerAvatar(
            initials = opponent.name.substringAfterLast(" ").take(1),
            name = opponent.name,
            size = 42.dp,
            isActive = opponent.isActive,
            backgroundColor = ArenaPalette.Blue,
            avatarId = if (opponent.name.endsWith("1")) "comet" else "spark"
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

private fun formatMatchDuration(durationMillis: Long): String {
    val seconds = durationMillis.coerceAtLeast(0L) / 1_000
    return "${seconds / 60}m ${seconds % 60}s"
}
