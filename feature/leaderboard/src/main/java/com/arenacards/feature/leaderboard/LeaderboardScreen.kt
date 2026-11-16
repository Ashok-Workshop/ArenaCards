package com.arenacards.feature.leaderboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton
import com.arenacards.designsystem.PlayerAvatar
import java.text.DateFormat
import java.util.Date

@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onOpenOnlineMatch: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹  Back") }
        Text(
            "ARENA  /  COMPETITIVE",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            "Rank among players",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        state.profile?.let { profile ->
            CompetitiveProfileCard(profile, reducedMotion)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LeaderboardScope.entries.forEach { scope ->
                val selected = state.scope == scope
                ArenaSecondaryButton(
                    text = if (scope == LeaderboardScope.GLOBAL) "Global" else "Friends",
                    onClick = { viewModel.selectScope(scope) },
                    modifier = Modifier.weight(1f),
                    enabled = !selected
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LeaderboardCategory.entries.forEach { category ->
                val selected = state.category == category
                ArenaSecondaryButton(
                    text = category.label,
                    onClick = { viewModel.selectCategory(category) },
                    enabled = !selected
                )
            }
        }
        if (state.isLoading && state.leaderboard == null) {
            Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.errorMessage != null && state.leaderboard == null) {
            ArenaPanel(Modifier.fillMaxWidth()) {
                state.errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                ArenaSecondaryButton("Retry", viewModel::refresh, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        } else {
            state.leaderboard?.let { page ->
                LeaderboardContents(
                    page = page,
                    loadingMore = state.isLoadingMore,
                    onLoadMore = viewModel::loadNextPage
                )
            }
        }
        if (state.errorMessage != null && state.leaderboard != null) {
            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            ArenaSecondaryButton("Retry", viewModel::refresh)
        }
        state.actionMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
        }
        TournamentSection(
            state = state,
            viewModel = viewModel,
            onOpenOnlineMatch = onOpenOnlineMatch
        )
    }
}

@Composable
private fun CompetitiveProfileCard(profile: CompetitiveProfile, reducedMotion: Boolean) {
    val content: @Composable ColumnScope.() -> Unit = {
        Text(
            "YOUR COMPETITIVE CARD",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlayerAvatar(
                initials = profile.avatarId?.take(1) ?: "A",
                name = profile.displayName ?: "Arena player",
                size = 58.dp,
                backgroundColor = ArenaPalette.Purple,
                avatarId = profile.avatarId
            )
            Column(Modifier.weight(1f)) {
                Text(profile.displayName ?: "Competitive player", style = MaterialTheme.typography.titleLarge)
                Text(profile.gamerId ?: "Gamer ID unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "✦ ${profile.rating}",
                color = ArenaPalette.Gold,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatValue("MATCHES", profile.verifiedMatches.toString())
            StatValue("WINS", profile.wins.toString())
            StatValue("LOSSES", profile.losses.toString())
            StatValue("WIN RATE", "${(profile.winPercentage * 100).toInt()}%")
        }
        profile.season?.let { season ->
            Spacer(Modifier.height(12.dp))
            Text(
                "${season.title}  ·  ${season.points} pts  ·  ${season.rating} rating",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Season ends ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(season.endsAtMillis))}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } ?: Text(
            "No active season. All-time rating carries forward; seasonal rating resets when an owner activates a season.",
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (profile.achievements.isNotEmpty()) {
            Text(
                "Competitive achievements  ·  ${profile.achievements.joinToString { it.title }}",
                modifier = Modifier.padding(top = 12.dp),
                color = ArenaPalette.Gold,
                style = MaterialTheme.typography.bodySmall
            )
        }
        profile.tournamentParticipation.takeIf { it.isNotEmpty() }?.let { history ->
            Text(
                "Tournaments  ·  ${history.take(3).joinToString { entry ->
                    entry.place?.let { "#$it ${entry.tournamentName}" } ?: entry.tournamentName
                }}",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (reducedMotion) {
        ArenaPanel(Modifier.fillMaxWidth(), backgroundBrush = Brush.linearGradient(
            listOf(ArenaPalette.Purple.copy(alpha = 0.22f), ArenaPalette.Blue.copy(alpha = 0.12f))
        ), content = content)
    } else {
        ArenaPanel(
            Modifier.fillMaxWidth().animateContentSize(),
            backgroundBrush = Brush.linearGradient(
                listOf(ArenaPalette.Purple.copy(alpha = 0.22f), ArenaPalette.Blue.copy(alpha = 0.12f))
            ),
            content = content
        )
    }
}

@Composable
private fun LeaderboardContents(
    page: CompetitiveLeaderboardPage,
    loadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    ArenaPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    when (page.category) {
                        LeaderboardCategory.RATING -> "All-time rating"
                        LeaderboardCategory.WINS -> "Verified victories"
                        LeaderboardCategory.WIN_RATE -> "Best win rate · 10+ matches"
                        LeaderboardCategory.SEASON -> "Current season"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(page.lastUpdatedAtMillis))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            page.currentPlayer?.rank?.let { rank ->
                Text("YOUR PLACE  #$rank", color = ArenaPalette.Gold, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (page.entries.isEmpty()) {
            Text(
                if (page.category == LeaderboardCategory.WIN_RATE) {
                    "Win-rate rankings appear after 10 verified matches."
                } else "No public competitive players are ranked yet.",
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        page.entries.forEachIndexed { index, player ->
            LeaderboardEntry(
                player,
                Modifier.padding(top = if (index == 0) 16.dp else 8.dp)
            )
        }
        if (page.nextCursor != null) {
            ArenaSecondaryButton(
                text = if (loadingMore) "Loading…" else "Load more rankings",
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                enabled = !loadingMore
            )
        }
    }
}

@Composable
private fun LeaderboardEntry(player: CompetitivePlayer, modifier: Modifier = Modifier) {
    val accent = when (player.rank) {
        1 -> ArenaPalette.Gold
        2 -> Color(0xFFADB8CA)
        3 -> Color(0xFFD58D66)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    ArenaPanel(
        modifier = modifier.fillMaxWidth(),
        backgroundBrush = if (player.isCurrentPlayer) {
            Brush.horizontalGradient(listOf(ArenaPalette.Gold.copy(alpha = 0.22f), Color.Transparent))
        } else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("#${player.rank}", color = accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            PlayerAvatar(
                initials = player.avatarId.take(1),
                name = player.displayName,
                size = 42.dp,
                backgroundColor = accent,
                avatarId = player.avatarId
            )
            Column(Modifier.weight(1f)) {
                Text(player.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "@${player.username} · ${player.verifiedMatches} verified",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (player.seasonPoints > 0) "${player.seasonRating} · ${player.seasonPoints} pts"
                    else "${player.rating} rating",
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${player.wins}W · ${player.losses}L",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TournamentSection(
    state: LeaderboardUiState,
    viewModel: LeaderboardViewModel,
    onOpenOnlineMatch: (String) -> Unit
) {
    Text("Tournament arena", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Individual single-elimination events · no entry fee · verified matches only",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    if (state.selectedTournament != null) {
        TournamentDetailCard(
            state.selectedTournament,
            state.busyTournamentId == state.selectedTournament.tournamentId,
            viewModel,
            onOpenOnlineMatch
        )
        return
    }
    if (state.tournaments.isEmpty() && !state.isLoading) {
        ArenaPanel(Modifier.fillMaxWidth()) {
            Text("No tournaments are scheduled. New events appear here after an owner creates them.")
        }
    }
    state.tournaments.forEach { tournament ->
        TournamentCard(
            tournament = tournament,
            busy = state.busyTournamentId == tournament.tournamentId,
            onOpen = { viewModel.loadTournament(tournament.tournamentId) },
            onRegister = { viewModel.register(tournament.tournamentId) },
            onCancel = { viewModel.cancelRegistration(tournament.tournamentId) }
        )
    }
    if (state.hasMoreTournaments) {
        ArenaSecondaryButton(
            text = if (state.isLoadingMore) "Loading…" else "More tournaments",
            onClick = viewModel::loadMoreTournaments,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoadingMore
        )
    }
    AnimatedVisibility(state.isTournamentLoading) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun TournamentCard(
    tournament: TournamentSummary,
    busy: Boolean,
    onOpen: () -> Unit,
    onRegister: () -> Unit,
    onCancel: () -> Unit
) {
    ArenaPanel(
        Modifier.fillMaxWidth().animateContentSize(),
        backgroundBrush = Brush.linearGradient(
            listOf(ArenaPalette.Blue.copy(alpha = 0.18f), ArenaPalette.Purple.copy(alpha = 0.12f))
        )
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(tournament.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(tournament.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(tournament.status.replace('_', ' '), color = ArenaPalette.Gold, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "${tournament.participantCount}/${tournament.maxParticipants} players  ·  Starts ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(tournament.startsAtMillis))}",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Champion reward  ·  ${tournament.rewardCoins} coins  ·  ${tournament.rewardXp} XP",
            modifier = Modifier.padding(top = 4.dp),
            color = ArenaPalette.Gold,
            style = MaterialTheme.typography.labelMedium
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArenaSecondaryButton("Details / bracket", onOpen, Modifier.weight(1f))
            if (tournament.isRegistered) {
                ArenaSecondaryButton(
                    if (busy) "Saving…" else "Withdraw",
                    onCancel,
                    Modifier.weight(1f),
                    enabled = !busy && tournament.status == "OPEN"
                )
            } else {
                ArenaPrimaryButton(
                    if (busy) "Joining…" else "Register",
                    onRegister,
                    Modifier.weight(1f),
                    enabled = !busy && tournament.status == "OPEN"
                        && tournament.participantCount < tournament.maxParticipants
                )
            }
        }
    }
}

@Composable
private fun TournamentDetailCard(
    tournament: TournamentDetails,
    busy: Boolean,
    viewModel: LeaderboardViewModel,
    onOpenOnlineMatch: (String) -> Unit
) {
    ArenaPanel(Modifier.fillMaxWidth()) {
        TextButton(onClick = viewModel::closeTournament) { Text("‹  Tournament list") }
        Text(tournament.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "${tournament.participantCount}/${tournament.maxParticipants} players  ·  ${tournament.format.replace('_', ' ')}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        tournament.bracket?.let { bracket ->
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                bracket.rounds.forEachIndexed { roundIndex, round ->
                    Column(
                        modifier = Modifier.width(220.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Round ${roundIndex + 1}", style = MaterialTheme.typography.titleMedium)
                        round.forEach { match ->
                            ArenaPanel(Modifier.fillMaxWidth()) {
                                Text(
                                    when (match.status) {
                                        "COMPLETED" -> if (match.isBye) "BYE ADVANCEMENT" else "COMPLETED"
                                        "ACTIVE" -> "MATCH IN PROGRESS"
                                        else -> "UPCOMING MATCH"
                                    },
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                match.players.forEach { player ->
                                    Text(
                                        player?.second ?: "Open bracket slot",
                                        modifier = Modifier.padding(top = 4.dp),
                                        fontWeight = if (player?.first == match.winnerUid) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (match.canStart) {
                                    ArenaPrimaryButton(
                                        if (busy) "Preparing…" else "Play assigned match",
                                        onClick = {
                                            viewModel.startTournamentMatch(
                                                tournament.tournamentId,
                                                match.matchId,
                                                onOpenOnlineMatch
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        enabled = !busy
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } ?: Text(
            "Bracket is published after the organizer starts the event.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}
