package com.arenacards.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun createDeck_hasExpectedCardCount() {
        val deck = GameEngine.createDeck()
        assertEquals(108, deck.size)
    }

    @Test
    fun dealHands_returnsPlayerHands() {
        val hands = GameEngine.dealHands(playerCount = 3, cardsPerPlayer = 5)
        assertEquals(3, hands.size)
        assertEquals(5, hands[0].size)
        assertEquals(5, hands[1].size)
        assertEquals(5, hands[2].size)
    }

    @Test
    fun isPlayable_acceptsMatchingColorOrValue() {
        val top = GameCard(CardColor.RED, 7)
        val colorMatch = GameCard(CardColor.RED, 2)
        val valueMatch = GameCard(CardColor.BLUE, 7)
        val wild = GameCard(CardColor.WILD, 0, isWild = true)

        assertTrue(GameEngine.isPlayable(top, colorMatch))
        assertTrue(GameEngine.isPlayable(top, valueMatch))
        assertTrue(GameEngine.isPlayable(top, wild))
    }
}
