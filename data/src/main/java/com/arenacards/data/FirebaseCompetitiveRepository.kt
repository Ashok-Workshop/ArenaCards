package com.arenacards.data

import com.arenacards.feature.leaderboard.CompetitiveAchievement
import com.arenacards.feature.leaderboard.CompetitiveLeaderboardPage
import com.arenacards.feature.leaderboard.CompetitivePlayer
import com.arenacards.feature.leaderboard.CompetitiveProfile
import com.arenacards.feature.leaderboard.CompetitiveRepository
import com.arenacards.feature.leaderboard.CompetitiveSeason
import com.arenacards.feature.leaderboard.LeaderboardCategory
import com.arenacards.feature.leaderboard.LeaderboardCursor
import com.arenacards.feature.leaderboard.LeaderboardScope
import com.arenacards.feature.leaderboard.TournamentBracket
import com.arenacards.feature.leaderboard.TournamentCursor
import com.arenacards.feature.leaderboard.TournamentDetails
import com.arenacards.feature.leaderboard.TournamentHistoryEntry
import com.arenacards.feature.leaderboard.TournamentMatch
import com.arenacards.feature.leaderboard.TournamentPage
import com.arenacards.feature.leaderboard.TournamentSummary
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class FirebaseCompetitiveRepository : CompetitiveRepository {
    private val app = FirebaseApp.getInstance()
    private val functions = FirebaseFunctions.getInstance(
        app,
        FirebaseBackendConfig.FUNCTIONS_REGION
    )

    override suspend fun getLeaderboard(
        scope: LeaderboardScope,
        category: LeaderboardCategory,
        cursor: LeaderboardCursor?
    ): CompetitiveLeaderboardPage {
        val payload = mutableMapOf<String, Any>("category" to category.wireValue)
        if (cursor != null) payload["cursor"] = mapOf(
            "primary" to cursor.primary,
            "secondary" to cursor.secondary,
            "uid" to cursor.uid,
            "documentId" to cursor.documentId
        )
        val response = if (scope == LeaderboardScope.FRIENDS) {
            call("getFriendsLeaderboard", payload)
        } else {
            call("getCompetitiveLeaderboard", payload)
        }
        return response.toLeaderboardPage(category)
    }

    override suspend fun getProfile(): CompetitiveProfile {
        val response = call("getMyCompetitiveProfile", emptyMap())
        val seasonMap = response.optionalMap("season")
        val seasonStats = seasonMap?.optionalMap("stats")
        return CompetitiveProfile(
            gamerId = response.optionalString("gamerId"),
            displayName = response.optionalString("displayName"),
            avatarId = response.optionalString("avatarId"),
            rating = response.intValue("rating"),
            verifiedMatches = response.intValue("verifiedMatches"),
            wins = response.intValue("wins"),
            losses = response.intValue("losses"),
            winPercentage = response.doubleValue("winPercentage"),
            bestRating = response.intValue("bestRating", 1_000),
            bestRank = response.optionalInt("bestRank"),
            season = if (seasonMap != null) {
                CompetitiveSeason(
                    id = seasonMap.requiredString("id"),
                    title = seasonMap.requiredString("title"),
                    startsAtMillis = seasonMap.longValue("startsAtMillis"),
                    endsAtMillis = seasonMap.longValue("endsAtMillis"),
                    rating = seasonStats?.intValue("seasonRating", 1_000) ?: 1_000,
                    points = seasonStats?.intValue("seasonPoints") ?: 0,
                    matches = seasonStats?.intValue("verifiedMatches") ?: 0,
                    wins = seasonStats?.intValue("wins") ?: 0
                )
            } else null,
            achievements = response.requiredList("achievements").map { item ->
                val value = item.asMap("competitive achievement")
                CompetitiveAchievement(
                    id = value.requiredString("id"),
                    title = value.requiredString("title"),
                    unlockedAtMillis = value.optionalLong("unlockedAtMillis")
                )
            },
            tournamentParticipation = response.requiredList("tournamentParticipation").map { item ->
                val value = item.asMap("tournament history")
                TournamentHistoryEntry(
                    tournamentId = value.requiredString("tournamentId"),
                    tournamentName = value.requiredString("tournamentName"),
                    status = value.requiredString("status"),
                    place = value.optionalInt("place")
                )
            }
        )
    }

    override suspend fun getTournaments(cursor: TournamentCursor?): TournamentPage {
        val payload = mutableMapOf<String, Any>("pageSize" to PAGE_SIZE)
        if (cursor != null) payload["cursor"] = mapOf(
            "deadlineMillis" to cursor.deadlineMillis,
            "tournamentId" to cursor.tournamentId
        )
        val response = call("listTournaments", payload)
        return TournamentPage(
            tournaments = response.requiredList("tournaments").map { item ->
                val value = item.asMap("tournament")
                val champion = value.optionalMap("rewards")?.optionalMap("champion")
                TournamentSummary(
                    tournamentId = value.requiredString("tournamentId"),
                    title = value.requiredString("title"),
                    description = value.optionalString("description").orEmpty(),
                    status = value.requiredString("status"),
                    format = value.requiredString("format"),
                    participantCount = value.intValue("participantCount"),
                    maxParticipants = value.intValue("maxParticipants"),
                    registrationDeadlineMillis = value.longValue("registrationDeadlineMillis"),
                    startsAtMillis = value.longValue("startsAtMillis"),
                    rewardCoins = champion?.intValue("coins") ?: 0,
                    rewardXp = champion?.intValue("xp") ?: 0,
                    isRegistered = value.booleanValue("isRegistered")
                )
            },
            nextCursor = response.optionalMap("nextCursor")?.let { value ->
                TournamentCursor(
                    deadlineMillis = value.longValue("deadlineMillis"),
                    tournamentId = value.requiredString("tournamentId")
                )
            }
        )
    }

    override suspend fun getTournament(tournamentId: String): TournamentDetails {
        val response = call("getTournament", mapOf("tournamentId" to tournamentId))
        val champion = response.optionalMap("rewards")?.optionalMap("champion")
        val bracketMap = response.optionalMap("bracket")
        return TournamentDetails(
            tournamentId = response.requiredString("tournamentId"),
            title = response.requiredString("title"),
            description = response.optionalString("description").orEmpty(),
            status = response.requiredString("status"),
            format = response.requiredString("format"),
            participantCount = response.intValue("participantCount"),
            maxParticipants = response.intValue("maxParticipants"),
            registrationDeadlineMillis = response.longValue("registrationDeadlineMillis"),
            startsAtMillis = response.longValue("startsAtMillis"),
            rewardCoins = champion?.intValue("coins") ?: 0,
            rewardXp = champion?.intValue("xp") ?: 0,
            bracket = bracketMap?.toBracket()
        )
    }

    override suspend fun registerTournament(tournamentId: String) {
        call("registerTournament", mapOf("tournamentId" to tournamentId))
    }

    override suspend fun cancelTournamentRegistration(tournamentId: String) {
        call("cancelTournamentRegistration", mapOf("tournamentId" to tournamentId))
    }

    override suspend fun startTournamentMatch(
        tournamentId: String,
        tournamentMatchId: String
    ): String = call(
        "startTournamentMatch",
        mapOf(
            "tournamentId" to tournamentId,
            "tournamentMatchId" to tournamentMatchId,
            "requestId" to java.util.UUID.randomUUID().toString()
        )
    ).requiredString("matchId")

    private suspend fun call(name: String, payload: Map<String, Any>): Map<String, Any?> {
        val data = functions.getHttpsCallable(name).call(payload).await().getData()
        return data.asMap(name)
    }

    private fun Map<String, Any?>.toLeaderboardPage(
        category: LeaderboardCategory
    ): CompetitiveLeaderboardPage {
        val playerEntries = requiredList("entries").map { item ->
            item.asMap("leaderboard entry").toCompetitivePlayer()
        }
        val current = optionalMap("currentPlayer")?.let { value ->
            playerEntries.firstOrNull { it.uid == value.optionalString("uid") }
                ?: value.toCompetitivePlayer()
        }
        return CompetitiveLeaderboardPage(
            category = category,
            seasonId = optionalString("seasonId"),
            entries = playerEntries,
            currentPlayer = current,
            lastUpdatedAtMillis = longValue("lastUpdatedAtMillis"),
            nextCursor = optionalMap("nextCursor")?.let { value ->
                LeaderboardCursor(
                    primary = value.doubleValue("primary"),
                    secondary = value.doubleValue("secondary"),
                    uid = value.requiredString("uid"),
                    documentId = value.optionalString("documentId")
                        ?: value.requiredString("uid")
                )
            }
        )
    }

    private fun Map<String, Any?>.toCompetitivePlayer() = CompetitivePlayer(
        uid = requiredString("uid"),
        gamerId = optionalString("gamerId").orEmpty(),
        username = optionalString("username").orEmpty(),
        displayName = optionalString("displayName").orEmpty(),
        avatarId = optionalString("avatarId") ?: "ember",
        rating = intValue("rating"),
        seasonRating = intValue("seasonRating", 1_000),
        seasonPoints = intValue("seasonPoints"),
        verifiedMatches = intValue("verifiedMatches"),
        wins = intValue("wins"),
        losses = intValue("losses"),
        winPercentage = doubleValue("winPercentage"),
        rank = intValue("rank"),
        isCurrentPlayer = booleanValue("isCurrentPlayer")
    )

    private fun Map<String, Any?>.toBracket() = TournamentBracket(
        format = requiredString("format"),
        championUid = optionalString("championUid"),
        rounds = requiredList("rounds").map { round ->
            round.asList("bracket round").map { item ->
                val value = item.asMap("bracket match")
                TournamentMatch(
                    matchId = value.requiredString("matchId"),
                    round = value.intValue("round"),
                    players = value.requiredList("players").map { player ->
                        if (player == null) null else {
                            val profile = player.asMap("bracket player")
                            profile.requiredString("uid") to profile.requiredString("displayName")
                        }
                    },
                    status = value.requiredString("status"),
                    winnerUid = value.optionalString("winnerUid"),
                    onlineMatchId = value.optionalString("onlineMatchId"),
                    isBye = value.booleanValue("isBye"),
                    canStart = value.booleanValue("canStart")
                )
            }
        }
    )

    private fun Any?.asMap(label: String): Map<String, Any?> =
        (this as? Map<*, *>)?.entries?.associate { (key, value) ->
            (key as? String ?: error("$label contains an invalid key.")) to value
        } ?: error("$label response is invalid.")

    private fun Any?.asList(label: String): List<Any?> =
        (this as? List<*>)?.map { it } ?: error("$label response is invalid.")

    private fun Map<String, Any?>.requiredList(key: String): List<Any?> =
        this[key].asList(key)

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key] as? String ?: error("Competitive response field '$key' is missing.")

    private fun Map<String, Any?>.optionalString(key: String): String? =
        this[key] as? String

    private fun Map<String, Any?>.requiredMap(key: String): Map<String, Any?> =
        this[key].asMap(key)

    private fun Map<String, Any?>.optionalMap(key: String): Map<String, Any?>? =
        (this[key] as? Map<*, *>)?.entries?.associate { (name, value) ->
            (name as? String ?: error("Competitive response contains an invalid key.")) to value
        }

    private fun Map<String, Any?>.longValue(key: String): Long =
        (this[key] as? Number)?.toLong() ?: error("Competitive response field '$key' is missing.")

    private fun Map<String, Any?>.optionalLong(key: String): Long? =
        (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.intValue(key: String, default: Int = 0): Int =
        (this[key] as? Number)?.toInt() ?: default

    private fun Map<String, Any?>.optionalInt(key: String): Int? =
        (this[key] as? Number)?.toInt()

    private fun Map<String, Any?>.doubleValue(key: String): Double =
        (this[key] as? Number)?.toDouble() ?: 0.0

    private fun Map<String, Any?>.booleanValue(key: String): Boolean =
        this[key] as? Boolean ?: false

    private companion object {
        const val PAGE_SIZE = 30
    }
}
