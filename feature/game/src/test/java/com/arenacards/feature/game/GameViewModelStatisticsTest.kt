package com.arenacards.feature.game

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

class GameViewModelStatisticsTest {
    @Test
    fun restoredCompletedMatchIsRecordedOnceAsALocalWin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val finishedMatch = GameSession(
                hands = listOf(
                    emptyList(),
                    listOf(GameCard(CardColor.BLUE, 3))
                ),
                drawPile = emptyList(),
                discardPile = listOf(GameCard(CardColor.RED, 4)),
                currentPlayerIndex = 0,
                direction = 1,
                activeColor = CardColor.RED,
                winnerIndex = 0
            )
            val repository = RecordingPracticeStatisticsRepository()
            val savedState = SavedStateHandle(
                mapOf("offline_game_session" to GameEngine.encodeSession(finishedMatch))
            )

            val viewModel = GameViewModel(savedState, repository)
            advanceUntilIdle()

            assertEquals(1, repository.records.size)
            assertEquals(PracticeResult.WIN, repository.records.single().result)
            assertEquals(
                viewModel.uiState.value.matchId,
                savedState.get<String>("offline_game_recorded_match")
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class RecordingPracticeStatisticsRepository : PracticeStatisticsRepository {
        val records = mutableListOf<PracticeMatchRecord>()
        override val statistics: Flow<PracticeStatistics> = MutableStateFlow(PracticeStatistics())

        override suspend fun recordMatch(record: PracticeMatchRecord) {
            records += record
        }
    }
}
