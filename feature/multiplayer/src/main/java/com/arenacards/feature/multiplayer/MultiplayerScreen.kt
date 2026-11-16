package com.arenacards.feature.multiplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.arenacards.feature.friends.SocialPlayer
import com.arenacards.feature.friends.SocialRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MultiplayerScreen(
    viewModel: MultiplayerViewModel,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onFeedback: (MultiplayerFeedback) -> Unit,
    socialRepository: SocialRepository? = null,
    onOpenRoomChat: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var roomCode by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf<OnlineCard?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showLeaveRoomConfirmation by remember { mutableStateOf(false) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    BackHandler {
        if (showHistory) showHistory = false else showExitConfirmation = true
    }

    LaunchedEffect(state.feedbackSequence) {
        state.feedbackEvent?.let(onFeedback)
    }
    LaunchedEffect(showHistory) {
        if (showHistory) viewModel.refreshHistory()
    }
    val match = state.match
    val room = state.room
    LaunchedEffect(match?.matchId, match?.turnNumber, match?.stateVersion, match?.turnDeadlineMillis) {
        val activeMatch = match ?: return@LaunchedEffect
        if (activeMatch.status != "ACTIVE") return@LaunchedEffect
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            val deadline = activeMatch.turnDeadlineMillis ?: break
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) {
                viewModel.processExpiredTurn()
                kotlinx.coroutines.delay(5_000)
            } else {
                kotlinx.coroutines.delay(minOf(remaining, 1_000L))
            }
        }
    }

    ArenaTableBackdrop(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArenaCardsLogoMark(Modifier.size(42.dp))
                    Column {
                        Text(
                            "ARENA • LIVE",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text("Online table", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                ArenaSecondaryButton(text = "‹ Back", onClick = { showExitConfirmation = true })
            }

            state.errorMessage?.let { message ->
                ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    ArenaSecondaryButton(
                        text = "Dismiss",
                        onClick = viewModel::dismissMessage,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            state.noticeMessage?.let { message ->
                Text(
                    message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            when {
                showHistory -> MatchHistoryContent(
                    state = state,
                    onBack = { showHistory = false },
                    onLoadMore = viewModel::loadMoreHistory
                )
                match != null -> OnlineMatchTable(
                    match = match,
                    currentUid = viewModel.currentUid,
                    onPlayCard = { card ->
                        if (card.isWild) showColorDialog = card
                        else viewModel.submitAction("PLAY_CARD", card = card)
                    },
                    onDraw = { viewModel.submitAction("DRAW_CARD") },
                    onPass = { viewModel.submitAction("PASS_TURN") },
                    onChallenge = { viewModel.submitAction("CHALLENGE") },
                    onAcceptPenalty = { viewModel.submitAction("ACCEPT_PENALTY") },
                    onCallUno = { viewModel.submitAction("CALL_UNO") },
                    onCatchUno = { viewModel.submitAction("CATCH_UNO") },
                    currentTimeMillis = currentTimeMillis,
                    isSubmitting = state.isSubmittingAction,
                    pendingActionVersion = state.pendingActionVersion,
                    reducedMotion = reducedMotion,
                    onShowHistory = { showHistory = true }
                )
                room != null -> PrivateRoomContent(
                    room = room,
                    currentUid = viewModel.currentUid,
                    isBusy = state.isBusy,
                    onReady = viewModel::setReady,
                    onLeave = { showLeaveRoomConfirmation = true },
                    onStart = viewModel::startRoom,
                    onUpdateRules = viewModel::updateRoomRules,
                    socialRepository = socialRepository,
                    onOpenRoomChat = onOpenRoomChat
                )
                state.playerStatus.status == "QUEUED" -> MatchmakingWaitingContent(
                    isBusy = state.isBusy,
                    playerCount = state.playerCount,
                    expiresAtMillis = state.playerStatus.queueExpiresAtMillis,
                    onCancel = viewModel::cancelMatchmaking
                )
                else -> MultiplayerLobbyContent(
                    playerCount = state.playerCount,
                    roomCode = roomCode,
                    isBusy = state.isBusy,
                    historyLoading = state.isLoadingHistory,
                    onPlayerCount = viewModel::selectPlayerCount,
                    onRoomCode = { roomCode = it.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit).take(10) },
                    onFindMatch = viewModel::joinMatchmaking,
                    onCreateRoom = viewModel::createRoom,
                    onJoinRoom = { viewModel.joinRoom(roomCode) },
                    onHistory = { showHistory = true }
                )
            }
        }
    }
    }

    if (showExitConfirmation) {
        val exitMessage = when {
            match?.status == "ACTIVE" ->
                "Your online match will continue on the server and its turn timer keeps running. " +
                    "You can return to this screen to reconnect."
            room != null ->
                "Your private-room seat will remain reserved. Use Leave room if you want to give up your seat."
            state.playerStatus.status == "QUEUED" ->
                "Your matchmaking search remains active. Cancel the search first if you do not want to keep waiting."
            else -> "No online match action is in progress."
        }
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Leave multiplayer?") },
            text = { Text(exitMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onBack()
                }) { Text("Return") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Stay here") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showLeaveRoomConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveRoomConfirmation = false },
            title = { Text("Leave this private room?") },
            text = { Text("Your seat will be released and you will return to the multiplayer lobby.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveRoomConfirmation = false
                    viewModel.leaveRoom()
                }) { Text("Leave room") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveRoomConfirmation = false }) { Text("Stay") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    showColorDialog?.let { card ->
        AlertDialog(
            onDismissRequest = { showColorDialog = null },
            title = { Text("Choose the next color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("RED", "BLUE", "GREEN", "YELLOW").forEach { color ->
                        ArenaSecondaryButton(
                            text = color.lowercase().replaceFirstChar(Char::uppercase),
                            onClick = {
                                showColorDialog = null
                                viewModel.submitAction("PLAY_CARD", card = card, color = color)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun MultiplayerLobbyContent(
    playerCount: Int,
    roomCode: String,
    isBusy: Boolean,
    historyLoading: Boolean,
    onPlayerCount: (Int) -> Unit,
    onRoomCode: (String) -> Unit,
    onFindMatch: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onHistory: () -> Unit
) {
    Text(
        "Real players • server-dealt cards • private hands",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
    ArenaPanel(Modifier.fillMaxWidth()) {
        Text("Choose your table", style = MaterialTheme.typography.titleLarge)
        Text(
            "The selected size is the total number of human players. ArenaCards will wait instead of adding CPU seats.",
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..4).forEach { count ->
                if (count == playerCount) {
                    ArenaPrimaryButton("$count", { onPlayerCount(count) }, Modifier.weight(1f))
                } else {
                    ArenaSecondaryButton("$count", { onPlayerCount(count) }, Modifier.weight(1f))
                }
            }
        }
    }

    ArenaPanel(Modifier.fillMaxWidth(), backgroundBrush = Brush.linearGradient(
        listOf(ArenaPalette.Purple.copy(alpha = 0.28f), ArenaPalette.Blue.copy(alpha = 0.16f))
    )) {
        Text("Quick matchmaking", style = MaterialTheme.typography.titleLarge)
        Text(
            "Join the ${playerCount}-player queue. Matchmaking and seating are controlled by the server.",
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ArenaPrimaryButton(
            text = if (isBusy) "Connecting…" else "Find real players",
            onClick = onFindMatch,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        )
    }

    ArenaPanel(Modifier.fillMaxWidth()) {
        Text("Private table", style = MaterialTheme.typography.titleLarge)
        Text(
            "Create a room and share its high-entropy code with people you trust.",
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ArenaSecondaryButton(
            text = if (isBusy) "Creating…" else "Create private room",
            onClick = onCreateRoom,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = roomCode,
            onValueChange = onRoomCode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            label = { Text("10-character room code") },
            singleLine = true,
            supportingText = { Text("${roomCode.length}/10") }
        )
        ArenaPrimaryButton(
            text = if (isBusy) "Joining…" else "Join room",
            onClick = onJoinRoom,
            enabled = !isBusy && roomCode.length == 10,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
    ArenaSecondaryButton(
        text = if (historyLoading) "Loading match history…" else "Verified match history",
        onClick = onHistory,
        enabled = !historyLoading,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MatchmakingWaitingContent(
    isBusy: Boolean,
    playerCount: Int,
    expiresAtMillis: Long?,
    onCancel: () -> Unit
) {
    ArenaPanel(Modifier.fillMaxWidth()) {
        CircularProgressIndicator()
        Text(
            "Finding your $playerCount-player table",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Your seat is reserved while the server searches. Other seats are still open; ArenaCards will not add bots.",
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { 1f / playerCount },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                .semantics {
                    contentDescription = "Your seat reserved; searching for ${playerCount - 1} other players"
                }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(playerCount) { seat ->
                Column(
                    modifier = Modifier.weight(1f).clip(CircleShape)
                        .background(
                            if (seat == 0) ArenaPalette.Purple.copy(alpha = 0.26f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (seat == 0) "YOU" else "OPEN",
                        color = if (seat == 0) ArenaPalette.Gold
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (seat == 0) "Reserved" else "Searching",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        expiresAtMillis?.let {
            Text(
                "Queue expires ${formatDate(it)}",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        ArenaSecondaryButton(
            text = "Cancel search",
            onClick = onCancel,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
    }
}

@Composable
private fun PrivateRoomContent(
    room: OnlineRoomSnapshot,
    currentUid: String,
    isBusy: Boolean,
    onReady: (Boolean) -> Unit,
    onLeave: () -> Unit,
    onStart: () -> Unit,
    onUpdateRules: (Long, Boolean) -> Unit,
    socialRepository: SocialRepository?,
    onOpenRoomChat: (String) -> Unit
) {
    var friends by remember(room.roomId) { mutableStateOf<List<SocialPlayer>>(emptyList()) }
    var socialError by remember(room.roomId) { mutableStateOf<String?>(null) }
    var socialNotice by remember(room.roomId) { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(room.roomId, socialRepository, currentUid == room.ownerUid) {
        if (socialRepository != null && currentUid == room.ownerUid) {
            try {
                friends = socialRepository.listFriends().items
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                socialError = error.message ?: "Friends could not be loaded."
            }
        }
    }

    ArenaPanel(Modifier.fillMaxWidth()) {
        Text("PRIVATE ROOM", color = MaterialTheme.colorScheme.secondary)
        Text(
            room.roomCode,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            color = ArenaPalette.Gold,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Share this code privately. It expires after 24 hours and cannot be used after the match starts.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Seats ${room.players.size}/${room.capacity}",
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.titleMedium
        )
        room.players.forEach { player ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerAvatar(
                    initials = player.displayName,
                    name = player.displayName,
                    size = 42.dp,
                    isActive = player.ready,
                    avatarId = player.avatarId
                )
                Column(Modifier.weight(1f)) {
                    Text(player.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        player.gamerId,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    when {
                        player.uid == room.ownerUid -> "HOST"
                        player.ready -> "READY"
                        else -> "WAITING"
                    },
                    color = if (player.ready) ArenaPalette.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        repeat((room.capacity - room.players.size).coerceAtLeast(0)) { seat ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerAvatar(
                    initials = "+",
                    name = "Open seat ${seat + 1}",
                    size = 42.dp,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(Modifier.weight(1f)) {
                    Text("Open seat", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Waiting for a real player",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        ArenaSecondaryButton(
            text = "Open room chat",
            onClick = { onOpenRoomChat(room.roomId) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        if (currentUid == room.ownerUid && socialRepository != null) {
            Text(
                "Invite a friend",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium
            )
            friends.filterNot { friend -> friend.uid in room.memberUids }.forEach { friend ->
                ArenaSecondaryButton(
                    text = "Invite ${friend.displayName}",
                    onClick = {
                        scope.launch {
                            try {
                                socialRepository.inviteFriendToRoom(
                                    room.roomId,
                                    friend.uid,
                                    UUID.randomUUID().toString()
                                )
                                socialNotice = "Room invitation sent."
                                socialError = null
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                socialError = error.message ?: "The room invitation could not be sent."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
            socialError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }
            socialNotice?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (currentUid == room.ownerUid && room.status == "WAITING") {
            Text(
                "Table rules",
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Standard mode • card stacking disabled",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(30_000L, 45_000L, 60_000L).forEach { duration ->
                    val selected = room.turnDurationMillis == duration
                    val label = "${duration / 1_000}s"
                    if (selected) {
                        ArenaPrimaryButton(
                            text = label,
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ArenaSecondaryButton(
                            text = label,
                            onClick = {
                                onUpdateRules(duration, room.allowWildDrawFourChallenge)
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Allow Wild Draw Four challenges",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.material3.Switch(
                    checked = room.allowWildDrawFourChallenge,
                    onCheckedChange = {
                        onUpdateRules(room.turnDurationMillis, it)
                    },
                    enabled = !isBusy
                )
            }
        }
        if (currentUid != room.ownerUid) {
            val ready = room.players.firstOrNull { it.uid == currentUid }?.ready == true
            ArenaPrimaryButton(
                text = if (ready) "Set not ready" else "Ready up",
                onClick = { onReady(!ready) },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        } else {
            ArenaPrimaryButton(
                text = "Start server-dealt match",
                onClick = onStart,
                enabled = !isBusy && room.players.size >= 2 && room.players.all { it.ready },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        }
        ArenaSecondaryButton(
            text = "Leave room",
            onClick = onLeave,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun OnlineMatchTable(
    match: OnlineMatchSnapshot,
    currentUid: String,
    onPlayCard: (OnlineCard) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit,
    onChallenge: () -> Unit,
    onAcceptPenalty: () -> Unit,
    onCallUno: () -> Unit,
    onCatchUno: () -> Unit,
    currentTimeMillis: Long,
    isSubmitting: Boolean,
    pendingActionVersion: Int?,
    reducedMotion: Boolean,
    onShowHistory: () -> Unit
) {
    val isMyTurn = match.currentPlayerUid == currentUid
    val remainingMillis = ((match.turnDeadlineMillis ?: currentTimeMillis) - currentTimeMillis).coerceAtLeast(0)
    val unoRemainingMillis =
        ((match.unoDeadlineMillis ?: currentTimeMillis) - currentTimeMillis).coerceAtLeast(0)
    val unoWindowOpen = match.pendingUnoPlayerUid != null &&
        match.unoDeadlineMillis != null && unoRemainingMillis > 0L
    var discardBounds by remember(match.matchId, match.stateVersion) { mutableStateOf<Rect?>(null) }
    var isDraggingCard by remember { mutableStateOf(false) }
    val canDraw = match.status == "ACTIVE" && isMyTurn && !match.challengePending &&
        !match.hasDrawnThisTurn && !isSubmitting
    val noPlayableCard = isMyTurn && match.status == "ACTIVE" && !match.challengePending &&
        !match.hasDrawnThisTurn && match.hand.none { isPlayable(match, it) }
    ArenaPanel(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        backgroundBrush = Brush.linearGradient(
            listOf(ColorDeepNavy, ColorTableGreen.copy(alpha = 0.52f), ColorDeepNavy)
        ),
        contentColor = Color.White
    ) {
        Text(
            when {
                match.status == "ACTIVE" && isMyTurn -> "YOUR TURN"
                match.status == "ACTIVE" && match.challengePending && match.challengeTargetUid == currentUid ->
                    "WILD DRAW FOUR • RESPOND"
                match.status == "ACTIVE" -> "OPPONENT TURN"
                match.status == "ABANDONED" -> "MATCH ABANDONED"
                else -> "MATCH COMPLETE"
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (isMyTurn) ArenaPalette.Gold else MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Match ${match.matchId.take(8)} • Turn ${match.turnNumber} • " +
                "${if (match.direction > 0) "clockwise" else "counter-clockwise"}",
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            match.players.sortedBy { it.seat }.forEach { player ->
                val stale = player.lastSeenAtMillis?.let {
                    currentTimeMillis - it > CONNECTION_STALE_MILLIS
                } == true
                Column(
                    modifier = Modifier.weight(1f).animateContentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerAvatar(
                        initials = player.displayName,
                        name = player.displayName,
                        size = if (player.uid == currentUid) 48.dp else 40.dp,
                        isActive = player.uid == match.currentPlayerUid,
                        backgroundColor = playerColor(player.seat),
                        avatarId = player.avatarId
                    )
                    Text(
                        player.displayName.take(12),
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "${player.cardCount} cards • ${if (stale) "RECONNECTING" else "ONLINE"}",
                        color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (canDraw && noPlayableCard) {
                    Text("↓ DRAW", color = ArenaPalette.Gold, fontWeight = FontWeight.Bold)
                }
                Text("DRAW ${match.drawPileCount}", style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .border(
                            width = if (canDraw && noPlayableCard) 3.dp else 1.dp,
                            color = if (canDraw && noPlayableCard) ArenaPalette.Gold
                            else Color.White.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = canDraw, onClick = onDraw)
                        .padding(5.dp)
                        .semantics {
                            contentDescription =
                                if (canDraw) "Draw a card from the server deck"
                                else "Server draw pile, ${match.drawPileCount} cards"
                        }
                ) {
                    ArenaCardBack(width = 62.dp, height = 88.dp)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DISCARD • ${match.activeColor.lowercase()}", style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .onGloballyPositioned { discardBounds = it.boundsInRoot() }
                        .border(
                            width = if (isDraggingCard) 3.dp else 1.dp,
                            color = if (isDraggingCard) ArenaPalette.Gold
                            else Color.White.copy(alpha = 0.32f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                        )
                        .padding(5.dp)
                ) {
                    ArenaCardReveal(
                        color = match.topCard.toDesignColor(),
                        value = match.topCard.value,
                        eventId = match.topCard.id,
                        width = 64.dp,
                        height = 92.dp,
                        reducedMotion = reducedMotion
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TURN TIMER", style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (remainingMillis < 10_000) MaterialTheme.colorScheme.error else ArenaPalette.Gold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${(remainingMillis / 1000).toInt()}s",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = when {
                    isDraggingCard -> "Release on the glowing discard pile to send your move."
                    match.challengePending && isMyTurn -> "Choose Challenge or Accept +4 below."
                    isMyTurn && noPlayableCard -> "No playable cards. Draw one card from the deck."
                    isMyTurn -> "Tap a playable card or drag it to the discard pile."
                    else -> "Waiting for the server-confirmed active player."
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = Color(0xFFDCE4F4),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }

        match.result?.let { result ->
            ArenaPanel(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                backgroundBrush = Brush.linearGradient(
                    listOf(ArenaPalette.Gold.copy(alpha = 0.22f), ArenaPalette.Purple.copy(alpha = 0.18f))
                )
            ) {
                Text(
                    when {
                        !result.verified -> "Result pending verification"
                        result.winnerUid == currentUid -> "Arena victory"
                        result.winnerUid == null -> "No winner"
                        else -> "Match complete"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (result.verified) "Server-verified • ${formatDuration(result.durationMillis)}"
                    else "This result did not qualify for rewards.",
                    modifier = Modifier.padding(top = 5.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.positions.sortedBy { it.position }.forEach { position ->
                    val player = match.players.firstOrNull { it.uid == position.uid }
                    Text(
                        "${position.position}. ${player?.displayName ?: "Player"} • " +
                            "${position.remainingCards} cards remaining",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        AnimatedVisibility(visible = match.status == "ACTIVE" && isSubmitting) {
            Text(
                "Move pending server confirmation — the table has not been changed optimistically.",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        pendingActionVersion?.let { expected ->
            if (match.stateVersion < expected) {
                Text(
                    "Confirming move…",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (match.status == "ACTIVE" && unoWindowOpen) {
        val isUnoOffender = match.pendingUnoPlayerUid == currentUid
        ArenaPanel(
            modifier = Modifier.fillMaxWidth(),
            backgroundBrush = Brush.linearGradient(
                listOf(Color(0xFF493A19), Color(0xFF2D2940))
            ),
            contentColor = Color.White
        ) {
            Text(
                if (isUnoOffender) "One card left — announce UNO!"
                else "A player has one card and has not announced UNO.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Catch window: ${(unoRemainingMillis + 999L) / 1_000L} seconds.",
                modifier = Modifier.padding(top = 4.dp),
                color = Color(0xFFDCE4F4),
                style = MaterialTheme.typography.bodyMedium
            )
            if (isUnoOffender) {
                ArenaPrimaryButton(
                    text = "UNO!",
                    onClick = onCallUno,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            } else {
                ArenaSecondaryButton(
                    text = "Catch missed UNO (+2)",
                    onClick = onCatchUno,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
        }
    }

    if (match.status == "ACTIVE" && isMyTurn && match.challengePending) {
        ArenaPanel(Modifier.fillMaxWidth()) {
            Text("Challenge the previous Wild Draw Four, or accept four cards.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                ArenaPrimaryButton("Challenge", onChallenge, Modifier.weight(1f), !isSubmitting)
                ArenaSecondaryButton("Accept +4", onAcceptPenalty, Modifier.weight(1f), !isSubmitting)
            }
        }
    } else if (match.status == "ACTIVE" && isMyTurn) {
        ArenaPanel(Modifier.fillMaxWidth()) {
            Text("Your hand • ${match.hand.size}", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                match.hand.forEach { card ->
                    val canPlay = isPlayable(match, card) &&
                        (!match.hasDrawnThisTurn || card.id == match.drawnCardId)
                    ArenaCardTile(
                        color = card.toDesignColor(),
                        value = card.value,
                        enabled = canPlay && !isSubmitting,
                        onClick = { if (canPlay && !isSubmitting) onPlayCard(card) },
                        width = 66.dp,
                        height = 100.dp,
                        reducedMotion = reducedMotion,
                        dropTargetBounds = discardBounds,
                        onDrop = { if (!isSubmitting) onPlayCard(card) },
                        onDragStateChanged = { isDraggingCard = it }
                    )
                }
            }
            ArenaSecondaryButton(
                text = "Draw card",
                onClick = onDraw,
                enabled = canDraw,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            ArenaSecondaryButton(
                text = "End turn",
                onClick = onPass,
                enabled = match.hasDrawnThisTurn && !isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(
                "Tap or drag a playable card to act. Wild Draw Four can be challenged; stacking is disabled.",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        ArenaPanel(Modifier.fillMaxWidth()) {
            Text(
                if (match.status == "ACTIVE") "Waiting for the active player…"
                else "This match is finished. Competitive rewards were calculated by the server.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (match.status != "ACTIVE") {
                ArenaSecondaryButton(
                    text = "View verified history",
                    onClick = onShowHistory,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MatchHistoryContent(
    state: MultiplayerUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit
) {
    ArenaSecondaryButton("‹ Lobby", onBack)
    Text("Verified matches", style = MaterialTheme.typography.headlineSmall)
    if (state.history.isEmpty() && !state.isLoadingHistory) {
        ArenaPanel(Modifier.fillMaxWidth()) {
            Text("No completed online matches yet.")
            Text(
                "Only server-recorded matches appear here. Offline practice is kept separate.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    state.history.forEach { match ->
        ArenaPanel(Modifier.fillMaxWidth()) {
            Text(
                "${match.outcome.replace('_', ' ')} • ${match.matchId.take(8)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${match.finalPosition?.let { "Position $it • " }.orEmpty()}" +
                    "${formatDuration(match.durationMillis)} • ${formatDate(match.completedAtMillis)}",
                modifier = Modifier.padding(top = 3.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (match.verified) "Verified result" else "Not reward-eligible",
                modifier = Modifier.padding(top = 4.dp),
                color = if (match.verified) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
            match.players.forEach { player ->
                Text(
                    "${player.seat + 1}. ${player.displayName} • ${player.gamerId}",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    if (state.isLoadingHistory) {
        CircularProgressIndicator()
    }
    if (state.hasMoreHistory) {
        ArenaSecondaryButton(
            text = "Load older matches",
            onClick = onLoadMore,
            enabled = !state.isLoadingHistory,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun isPlayable(match: OnlineMatchSnapshot, card: OnlineCard): Boolean {
    if (card.value == 14) {
        return match.hand.none {
            it.id != card.id && !it.isWild && it.color == match.activeColor
        }
    }
    if (card.isWild || card.color == match.activeColor) return true
    return card.value == match.topCard.value
}

private fun OnlineCard.toDesignColor(): ArenaCardColor = when (color) {
    "RED" -> ArenaCardColor.RED
    "BLUE" -> ArenaCardColor.BLUE
    "GREEN" -> ArenaCardColor.GREEN
    "YELLOW" -> ArenaCardColor.YELLOW
    else -> ArenaCardColor.WILD
}

private fun playerColor(seat: Int) = when (seat % 4) {
    0 -> ArenaPalette.Purple
    1 -> ArenaPalette.Blue
    2 -> ArenaPalette.Green
    else -> ArenaPalette.Gold
}

private fun formatDuration(durationMillis: Long): String {
    val seconds = durationMillis.coerceAtLeast(0) / 1_000
    return "${seconds / 60}m ${seconds % 60}s"
}

private fun formatDate(millis: Long?): String =
    millis?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
        ?: "Date unavailable"

private val ColorDeepNavy = androidx.compose.ui.graphics.Color(0xFF0D152B)
private val ColorTableGreen = androidx.compose.ui.graphics.Color(0xFF147657)
private const val CONNECTION_STALE_MILLIS = 65_000L
