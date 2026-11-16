package com.arenacards.feature.game

import androidx.lifecycle.SavedStateHandle
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelTest {
    @Test
    fun seededViewModelsStartWithTheSameSession() {
        val first = GameViewModel(SavedStateHandle(), Random(7))
        val second = GameViewModel(SavedStateHandle(), Random(7))

        assertEquals(
            first.uiState.value.copy(matchId = "", matchDurationMillis = 0L),
            second.uiState.value.copy(matchId = "", matchDurationMillis = 0L)
        )
    }

    @Test
    fun startNewGameSupportsTwoThroughFourSeats() {
        val viewModel = GameViewModel(SavedStateHandle(), Random(1))

        (2..4).forEach { playerCount ->
            viewModel.startNewGame(playerCount)
            assertEquals(7, viewModel.uiState.value.playerCards.size)
            assertEquals(playerCount - 1, viewModel.uiState.value.opponents.size)
        }
    }

    @Test
    fun savedStateRestoresCurrentHandAndDrawnCard() {
        val savedState = SavedStateHandle()
        val original = GameViewModel(savedState, Random(12))
        original.drawCard()
        val originalState = original.uiState.value

        val restored = GameViewModel(savedState, Random(90)).uiState.value

        assertEquals(originalState.playerCards, restored.playerCards)
        assertEquals(originalState.topCard, restored.topCard)
        assertEquals(originalState.drawPileCount, restored.drawPileCount)
        assertEquals(originalState.hasDrawnThisTurn, restored.hasDrawnThisTurn)
        assertEquals(originalState.playableCardIndices, restored.playableCardIndices)
    }

    @Test
    fun savedStateRestoresPlayerCountAndDifficulty() {
        val savedState = SavedStateHandle()
        val original = GameViewModel(savedState, Random(17))
        original.startNewGame(4)
        original.setDifficulty(CpuDifficulty.HARD)
        val originalHand = original.uiState.value.playerCards

        val restored = GameViewModel(savedState, Random(18))
        restored.ensurePlayerCount(4)

        assertEquals(3, restored.uiState.value.opponents.size)
        assertEquals(CpuDifficulty.HARD, restored.uiState.value.difficulty)
        assertEquals(originalHand, restored.uiState.value.playerCards)
    }

    @Test
    fun pauseAndResumePreserveTheMatch() {
        val viewModel = GameViewModel(SavedStateHandle(), Random(21))
        val hand = viewModel.uiState.value.playerCards

        viewModel.pauseGame()
        assertTrue(viewModel.uiState.value.isPaused)
        viewModel.resumeGame()

        assertEquals(hand, viewModel.uiState.value.playerCards)
        assertTrue(!viewModel.uiState.value.isPaused)
    }

    @Test
    fun cpuUiShowsPublicCardCountWithoutRevealingHandScore() {
        val viewModel = GameViewModel(SavedStateHandle(), Random(22))

        assertEquals(1, viewModel.uiState.value.opponents.size)
        assertEquals(7, viewModel.uiState.value.opponents.single().cardCount)
    }

    @Test
    fun restoredPendingWildDrawFourCanBeChallengedFromTheUiState() {
        val savedState = SavedStateHandle()
        val pending = GameSession(
            hands = listOf(
                listOf(GameCard(CardColor.BLUE, 1)),
                listOf(GameCard(CardColor.RED, 2)),
                listOf(GameCard(CardColor.GREEN, 3))
            ),
            drawPile = (4..12).map { GameCard(CardColor.YELLOW, it) },
            discardPile = listOf(
                GameCard(CardColor.RED, 5),
                GameCard(CardColor.WILD, GameCard.WILD_DRAW_FOUR, isWild = true)
            ),
            currentPlayerIndex = 0,
            direction = 1,
            activeColor = CardColor.BLUE,
            pendingWildDrawFourChallenge = WildDrawFourChallenge(
                offenderIndex = 1,
                targetIndex = 0,
                offenderHadActiveColor = true
            )
        )
        savedState["offline_game_session"] = GameEngine.encodeSession(pending)
        val viewModel = GameViewModel(savedState, Random(23))
        assertTrue(viewModel.uiState.value.wildDrawFourChallengeAvailable)

        viewModel.respondToWildDrawFourChallenge(challenge = true)

        assertTrue(!viewModel.uiState.value.wildDrawFourChallengeAvailable)
        assertEquals(0, viewModel.uiState.value.currentPlayerIndex)
        assertEquals(5, viewModel.uiState.value.opponents[0].cardCount)
    }
}
