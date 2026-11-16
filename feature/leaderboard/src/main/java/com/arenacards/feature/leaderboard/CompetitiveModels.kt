package com.arenacards.feature.leaderboard

enum class LeaderboardCategory(val wireValue: String, val label: String) {
    RATING("rating", "Rating"),
    WINS("wins", "Verified wins"),
    WIN_RATE("winRate", "Win rate"),
    SEASON("season", "Season")
}

enum class LeaderboardScope {
    GLOBAL,
    FRIENDS
}

data class LeaderboardCursor(
    val primary: Double,
    val secondary: Double,
    val uid: String,
    val documentId: String
)

data class CompetitivePlayer(
    val uid: String,
    val gamerId: String,
    val username: String,
    val displayName: String,
    val avatarId: String,
    val rating: Int,
    val seasonRating: Int,
    val seasonPoints: Int,
    val verifiedMatches: Int,
    val wins: Int,
    val losses: Int,
    val winPercentage: Double,
    val rank: Int,
    val isCurrentPlayer: Boolean
)

data class CompetitiveLeaderboardPage(
    val category: LeaderboardCategory,
    val seasonId: String?,
    val entries: List<CompetitivePlayer>,
    val currentPlayer: CompetitivePlayer?,
    val lastUpdatedAtMillis: Long,
    val nextCursor: LeaderboardCursor?
)

data class CompetitiveAchievement(
    val id: String,
    val title: String,
    val unlockedAtMillis: Long?
)

data class CompetitiveSeason(
    val id: String,
    val title: String,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val rating: Int,
    val points: Int,
    val matches: Int,
    val wins: Int
)

data class CompetitiveProfile(
    val gamerId: String?,
    val displayName: String?,
    val avatarId: String?,
    val rating: Int,
    val verifiedMatches: Int,
    val wins: Int,
    val losses: Int,
    val winPercentage: Double,
    val bestRating: Int,
    val bestRank: Int?,
    val season: CompetitiveSeason?,
    val achievements: List<CompetitiveAchievement>,
    val tournamentParticipation: List<TournamentHistoryEntry>
)

data class TournamentHistoryEntry(
    val tournamentId: String,
    val tournamentName: String,
    val status: String,
    val place: Int?
)

data class TournamentSummary(
    val tournamentId: String,
    val title: String,
    val description: String,
    val status: String,
    val format: String,
    val participantCount: Int,
    val maxParticipants: Int,
    val registrationDeadlineMillis: Long,
    val startsAtMillis: Long,
    val rewardCoins: Int,
    val rewardXp: Int,
    val isRegistered: Boolean
)

data class TournamentCursor(
    val deadlineMillis: Long,
    val tournamentId: String
)

data class TournamentPage(
    val tournaments: List<TournamentSummary>,
    val nextCursor: TournamentCursor?
)

data class TournamentMatch(
    val matchId: String,
    val round: Int,
    val players: List<Pair<String, String>?>,
    val status: String,
    val winnerUid: String?,
    val onlineMatchId: String?,
    val isBye: Boolean,
    val canStart: Boolean
)

data class TournamentBracket(
    val format: String,
    val championUid: String?,
    val rounds: List<List<TournamentMatch>>
)

data class TournamentDetails(
    val tournamentId: String,
    val title: String,
    val description: String,
    val status: String,
    val format: String,
    val participantCount: Int,
    val maxParticipants: Int,
    val registrationDeadlineMillis: Long,
    val startsAtMillis: Long,
    val rewardCoins: Int,
    val rewardXp: Int,
    val bracket: TournamentBracket?
)

interface CompetitiveRepository {
    suspend fun getLeaderboard(
        scope: LeaderboardScope,
        category: LeaderboardCategory,
        cursor: LeaderboardCursor?
    ): CompetitiveLeaderboardPage

    suspend fun getProfile(): CompetitiveProfile
    suspend fun getTournaments(cursor: TournamentCursor? = null): TournamentPage
    suspend fun getTournament(tournamentId: String): TournamentDetails
    suspend fun registerTournament(tournamentId: String)
    suspend fun cancelTournamentRegistration(tournamentId: String)
    suspend fun startTournamentMatch(tournamentId: String, tournamentMatchId: String): String
}
