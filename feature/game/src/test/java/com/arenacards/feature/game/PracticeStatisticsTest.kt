package com.arenacards.feature.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeStatisticsTest {
    @Test
    fun achievementsRequireTheirDocumentedPracticeMilestones() {
        val hardWin = PracticeMatchRecord(
            matchId = "hard-win",
            result = PracticeResult.WIN,
            difficulty = CpuDifficulty.HARD,
            durationMillis = 1L,
            finalHandScore = 0,
            completedAtMillis = 1L
        )
        val statistics = PracticeStatistics(
            gamesPlayed = 10,
            wins = 1,
            recentMatches = listOf(hardWin)
        )

        assertEquals(
            setOf(
                PracticeAchievement.FIRST_MATCH,
                PracticeAchievement.FIRST_WIN,
                PracticeAchievement.TEN_MATCHES,
                PracticeAchievement.HARD_WIN
            ),
            earnedPracticeAchievements(statistics)
        )
        assertEquals(emptySet<PracticeAchievement>(), earnedPracticeAchievements(PracticeStatistics()))
    }
}
