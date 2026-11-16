package com.arenacards.feature.leaderboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load fetches profile leaderboard and tournaments`() = runTest(dispatcher) {
        val repository = FakeCompetitiveRepository()
        val viewModel = LeaderboardViewModel(repository)
        advanceUntilIdle()

        assertEquals(1_200, viewModel.uiState.value.profile?.rating)
        assertEquals("player-a", viewModel.uiState.value.leaderboard?.entries?.single()?.uid)
        assertEquals("cup-1", viewModel.uiState.value.tournaments.single().tournamentId)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `scope and category changes request the matching leaderboard`() = runTest(dispatcher) {
        val repository = FakeCompetitiveRepository()
        val viewModel = LeaderboardViewModel(repository)
        advanceUntilIdle()

        viewModel.selectScope(LeaderboardScope.FRIENDS)
        advanceUntilIdle()
        viewModel.selectCategory(LeaderboardCategory.WIN_RATE)
        advanceUntilIdle()

        assertEquals(
            listOf(
                LeaderboardScope.GLOBAL to LeaderboardCategory.RATING,
                LeaderboardScope.FRIENDS to LeaderboardCategory.RATING,
                LeaderboardScope.FRIENDS to LeaderboardCategory.WIN_RATE
            ),
            repository.leaderboardRequests
        )
        assertEquals(LeaderboardScope.FRIENDS, viewModel.uiState.value.leaderboard?.let { repository.lastScope })
    }

    @Test
    fun `tournament registration updates state and match start returns control`() = runTest(dispatcher) {
        val repository = FakeCompetitiveRepository()
        val viewModel = LeaderboardViewModel(repository)
        advanceUntilIdle()
        viewModel.register("cup-1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tournaments.single().isRegistered)
        assertEquals("Registration confirmed.", viewModel.uiState.value.actionMessage)

        var startedMatch: String? = null
        viewModel.startTournamentMatch("cup-1", "r1-m1") { startedMatch = it }
        advanceUntilIdle()
        assertEquals("match-started", startedMatch)
        assertNull(viewModel.uiState.value.busyTournamentId)
    }

    @Test
    fun `failed load exposes an error and retry clears it`() = runTest(dispatcher) {
        val repository = FakeCompetitiveRepository().apply { failNextLeaderboard = true }
        val viewModel = LeaderboardViewModel(repository)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertNotNull(viewModel.uiState.value.leaderboard)
    }

    private class FakeCompetitiveRepository : CompetitiveRepository {
        var failNextLeaderboard = false
        var lastScope = LeaderboardScope.GLOBAL
        val leaderboardRequests = mutableListOf<Pair<LeaderboardScope, LeaderboardCategory>>()
        private var pageNumber = 0

        override suspend fun getLeaderboard(
            scope: LeaderboardScope,
            category: LeaderboardCategory,
            cursor: LeaderboardCursor?
        ): CompetitiveLeaderboardPage {
            if (failNextLeaderboard) {
                failNextLeaderboard = false
                error("Network unavailable")
            }
            lastScope = scope
            leaderboardRequests += scope to category
            pageNumber++
            val player = CompetitivePlayer(
                uid = if (cursor == null) "player-a" else "player-b",
                gamerId = "AR-123456",
                username = "arena",
                displayName = "Arena Player",
                avatarId = "ember",
                rating = 1_200,
                seasonRating = 1_000,
                seasonPoints = 0,
                verifiedMatches = 12,
                wins = 8,
                losses = 4,
                winPercentage = 8.0 / 12,
                rank = pageNumber,
                isCurrentPlayer = cursor == null
            )
            return CompetitiveLeaderboardPage(
                category = category,
                seasonId = null,
                entries = listOf(player),
                currentPlayer = player.takeIf { cursor == null },
                lastUpdatedAtMillis = 1L,
                nextCursor = if (cursor == null) {
                    LeaderboardCursor(1_200.0, 8.0, "player-a", "player-a")
                } else null
            )
        }

        override suspend fun getProfile() = CompetitiveProfile(
            gamerId = "AR-123456",
            displayName = "Arena Player",
            avatarId = "ember",
            rating = 1_200,
            verifiedMatches = 12,
            wins = 8,
            losses = 4,
            winPercentage = 8.0 / 12,
            bestRating = 1_200,
            bestRank = 1,
            season = null,
            achievements = emptyList(),
            tournamentParticipation = emptyList()
        )

        override suspend fun getTournaments(cursor: TournamentCursor?) = TournamentPage(
            tournaments = listOf(
                TournamentSummary(
                    tournamentId = "cup-1",
                    title = "Arena Cup",
                    description = "A test tournament",
                    status = "OPEN",
                    format = "SINGLE_ELIMINATION",
                    participantCount = 1,
                    maxParticipants = 8,
                    registrationDeadlineMillis = 10L,
                    startsAtMillis = 20L,
                    rewardCoins = 100,
                    rewardXp = 50,
                    isRegistered = false
                )
            ),
            nextCursor = null
        )

        override suspend fun getTournament(tournamentId: String) = TournamentDetails(
            tournamentId = tournamentId,
            title = "Arena Cup",
            description = "",
            status = "OPEN",
            format = "SINGLE_ELIMINATION",
            participantCount = 1,
            maxParticipants = 8,
            registrationDeadlineMillis = 10L,
            startsAtMillis = 20L,
            rewardCoins = 100,
            rewardXp = 50,
            bracket = null
        )

        override suspend fun registerTournament(tournamentId: String) = Unit
        override suspend fun cancelTournamentRegistration(tournamentId: String) = Unit
        override suspend fun startTournamentMatch(tournamentId: String, tournamentMatchId: String) =
            "match-started"
    }
}
