package com.arenacards.feature.game

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

enum class PracticeResult {
    WIN,
    LOSS,
    DRAW
}

data class PracticeMatchRecord(
    val matchId: String,
    val result: PracticeResult,
    val difficulty: CpuDifficulty,
    val durationMillis: Long,
    val finalHandScore: Int,
    val completedAtMillis: Long
)

data class PracticeStatistics(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val recentMatches: List<PracticeMatchRecord> = emptyList()
)

interface PracticeStatisticsRepository {
    val isEnabled: Boolean
        get() = true
    val statistics: Flow<PracticeStatistics>

    suspend fun recordMatch(record: PracticeMatchRecord)
}

object NoPracticeStatisticsRepository : PracticeStatisticsRepository {
    override val isEnabled: Boolean = false
    override val statistics: Flow<PracticeStatistics> = emptyFlow()

    override suspend fun recordMatch(record: PracticeMatchRecord) = Unit
}

enum class PracticeAchievement {
    FIRST_MATCH,
    FIRST_WIN,
    TEN_MATCHES,
    HARD_WIN
}

fun earnedPracticeAchievements(statistics: PracticeStatistics): Set<PracticeAchievement> =
    buildSet {
        if (statistics.gamesPlayed >= 1) add(PracticeAchievement.FIRST_MATCH)
        if (statistics.wins >= 1) add(PracticeAchievement.FIRST_WIN)
        if (statistics.gamesPlayed >= 10) add(PracticeAchievement.TEN_MATCHES)
        if (statistics.recentMatches.any {
                it.result == PracticeResult.WIN && it.difficulty == CpuDifficulty.HARD
            }
        ) {
            add(PracticeAchievement.HARD_WIN)
        }
    }
