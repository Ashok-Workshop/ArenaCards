package com.arenacards.feature.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun createDeck_hasClassic108CardComposition() {
        val deck = GameEngine.createDeck(Random(1))

        assertEquals(108, deck.size)
        CardColor.entries.filter { it != CardColor.WILD }.forEach { color ->
            assertEquals(1, deck.count { it.color == color && it.value == 0 })
            (1..9).forEach { value ->
                assertEquals(2, deck.count { it.color == color && it.value == value })
            }
            listOf(GameCard.SKIP, GameCard.REVERSE, GameCard.DRAW_TWO).forEach { value ->
                assertEquals(2, deck.count { it.color == color && it.value == value })
            }
        }
        assertEquals(4, deck.count { it.value == GameCard.WILD })
        assertEquals(4, deck.count { it.value == GameCard.WILD_DRAW_FOUR })
    }

    @Test
    fun dealHands_supportsTwoThreeAndFourPlayers() {
        (2..4).forEach { playerCount ->
            val hands = GameEngine.dealHands(playerCount, 7, Random(playerCount))
            assertEquals(playerCount, hands.size)
            assertTrue(hands.all { it.size == 7 })
            val dealtCards = hands.flatten()
            val availableCopies = GameEngine.createDeck(Random(playerCount)).groupingBy { it }.eachCount()
            assertEquals(playerCount * 7, dealtCards.size)
            assertTrue(dealtCards.groupingBy { it }.eachCount().all { (card, count) ->
                count <= availableCopies.getValue(card)
            })
        }
    }

    @Test
    fun dealHands_rejectsUnsupportedPlayerCounts() {
        listOf(1, 5).forEach { playerCount ->
            assertTrue(runCatching { GameEngine.dealHands(playerCount) }.isFailure)
        }
    }

    @Test
    fun newGame_dealsSevenAndStartsWithNumberCard() {
        val session = GameEngine.newGame(playerCount = 4, random = Random(42))

        assertEquals(4, session.hands.size)
        assertTrue(session.hands.all { it.size == 7 })
        assertEquals(1, session.discardPile.size)
        assertTrue(session.topCard.value in 0..9)
        assertEquals(108, session.hands.sumOf { it.size } + session.drawPile.size + session.discardPile.size)
    }

    @Test
    fun isPlayable_matchesActiveColorOrTopValueAndAllowsWild() {
        val top = GameCard(CardColor.RED, 7)
        assertTrue(GameEngine.isPlayable(top, GameCard(CardColor.RED, 2)))
        assertTrue(GameEngine.isPlayable(top, GameCard(CardColor.BLUE, 7)))
        assertTrue(GameEngine.isPlayable(top, GameCard(CardColor.WILD, GameCard.WILD, true)))
        assertFalse(GameEngine.isPlayable(top, GameCard(CardColor.BLUE, 3)))
    }

    @Test
    fun wildDrawFour_isInvalidWhilePlayerHasActiveColor() {
        val hand = listOf(
            GameCard(CardColor.WILD, GameCard.WILD_DRAW_FOUR, true),
            GameCard(CardColor.RED, 2)
        )

        assertFalse(
            GameEngine.isPlayable(
                topCard = GameCard(CardColor.RED, 5),
                candidate = hand[0],
                activeColor = CardColor.RED,
                hand = hand
            )
        )
    }

    @Test
    fun wildDrawFour_isValidWhenPlayerHasNoActiveColor() {
        val wild = GameCard(CardColor.WILD, GameCard.WILD_DRAW_FOUR, true)
        val hand = listOf(wild, GameCard(CardColor.BLUE, 2))

        assertTrue(
            GameEngine.isPlayable(
                topCard = GameCard(CardColor.RED, 5),
                candidate = wild,
                activeColor = CardColor.RED,
                hand = hand
            )
        )
    }

    @Test
    fun playCard_requiresSelectedColorForWildAndUpdatesActiveColor() {
        val wild = GameCard(CardColor.WILD, GameCard.WILD, true)
        val session = session(hands = listOf(listOf(wild, red(1)), listOf(blue(2))))

        assertNull(GameEngine.playCard(session, 0, 0))
        val played = GameEngine.playCard(session, 0, 0, CardColor.GREEN, Random(1))

        assertNotNull(played)
        assertEquals(CardColor.GREEN, played?.activeColor)
        assertEquals(CardColor.WILD, played?.topCard?.color)
    }

    @Test
    fun playCard_rejectsOutOfTurnInvalidAndNonmatchingMoves() {
        val session = session(hands = listOf(listOf(blue(2)), listOf(red(2))))

        assertNull(GameEngine.playCard(session, 1, 0))
        assertNull(GameEngine.playCard(session, 0, 1))
        assertNull(GameEngine.playCard(session, 0, 0))
    }

    @Test
    fun skipSkipsNextSeatInThreePlayerGame() {
        val session = session(
            hands = listOf(
                listOf(GameCard(CardColor.RED, GameCard.SKIP), blue(1)),
                listOf(red(2)),
                listOf(blue(3))
            )
        )

        val played = GameEngine.playCard(session, 0, 0, random = Random(1))

        assertEquals(2, played?.currentPlayerIndex)
    }

    @Test
    fun numberCardAdvancesClockwiseForTwoThreeAndFourPlayers() {
        (2..4).forEach { playerCount ->
            val hands = List(playerCount) { playerIndex ->
                listOf(if (playerIndex == 0) red(1) else blue(playerIndex), green(playerIndex + 1))
            }
            val session = session(hands = hands)

            val played = GameEngine.playCard(session, 0, 0, random = Random(playerCount))

            assertEquals(1, played?.currentPlayerIndex)
            assertEquals(1, played?.hands?.get(0)?.size)
        }
    }

    @Test
    fun reverseChangesDirectionAndInTwoPlayersSkipsOpponent() {
        val threePlayer = session(
            hands = listOf(
                listOf(GameCard(CardColor.RED, GameCard.REVERSE), blue(1)),
                listOf(red(2)),
                listOf(blue(3))
            )
        )
        val reversed = GameEngine.playCard(threePlayer, 0, 0, random = Random(1))
        assertEquals(-1, reversed?.direction)
        assertEquals(2, reversed?.currentPlayerIndex)

        val twoPlayer = session(
            hands = listOf(
                listOf(GameCard(CardColor.RED, GameCard.REVERSE), blue(1)),
                listOf(red(2))
            )
        )
        val skipped = GameEngine.playCard(twoPlayer, 0, 0, random = Random(1))
        assertEquals(0, skipped?.currentPlayerIndex)
        assertEquals(-1, skipped?.direction)
    }

    @Test
    fun drawTwoAddsPenaltyCardsAndSkipsTarget() {
        val session = session(
            hands = listOf(
                listOf(GameCard(CardColor.RED, GameCard.DRAW_TWO), blue(1)),
                listOf(red(2)),
                listOf(blue(3))
            ),
            drawPile = listOf(green(4), yellow(5))
        )

        val played = GameEngine.playCard(session, 0, 0, random = Random(1))

        assertEquals(3, played?.hands?.get(1)?.size)
        assertEquals(2, played?.currentPlayerIndex)
        assertTrue(played?.drawPile?.isEmpty() == true)
    }

    @Test
    fun wildDrawFourDrawsFourAndSkipsTarget() {
        val wildDrawFour = GameCard(CardColor.WILD, GameCard.WILD_DRAW_FOUR, true)
        val session = session(
            hands = listOf(listOf(wildDrawFour, blue(1)), listOf(red(2)), listOf(blue(3))),
            drawPile = listOf(green(4), yellow(5), blue(6), green(7))
        )

        val played = GameEngine.playCard(session, 0, 0, CardColor.BLUE, Random(1))

        assertEquals(5, played?.hands?.get(1)?.size)
        assertEquals(2, played?.currentPlayerIndex)
        assertEquals(CardColor.BLUE, played?.activeColor)
    }

    @Test
    fun emptyHandWinsAndScoreUsesOfficialCardValues() {
        val session = session(
            hands = listOf(listOf(red(1)), listOf(red(2), blue(3)))
        )

        val won = GameEngine.playCard(session, 0, 0, random = Random(1))

        assertEquals(0, won?.winnerIndex)
        assertEquals(20 + 20 + 20 + 50 + 50, GameEngine.calculateScore(listOf(
            red(GameCard.SKIP),
            red(GameCard.REVERSE),
            red(GameCard.DRAW_TWO),
            GameCard(CardColor.WILD, GameCard.WILD, true),
            GameCard(CardColor.WILD, GameCard.WILD_DRAW_FOUR, true)
        )))
    }

    @Test
    fun drawAllowsOnlyNewCardThenPassesToNextSeat() {
        val session = session(
            hands = listOf(listOf(red(2)), listOf(blue(3))),
            drawPile = listOf(green(4))
        )

        val drawn = GameEngine.drawForCurrentPlayer(session, 0, Random(1))
        assertEquals(2, drawn?.hands?.get(0)?.size)
        assertEquals(1, drawn?.drawnCardIndex)
        assertNull(GameEngine.playCard(drawn!!, 0, 0))

        val passed = GameEngine.passTurn(drawn, 0)
        assertEquals(1, passed?.currentPlayerIndex)
    }

    @Test
    fun drawReshufflesDiscardExceptTopCard() {
        val session = session(
            hands = listOf(listOf(red(2)), listOf(blue(3))),
            drawPile = emptyList(),
            discardPile = listOf(blue(8), green(5))
        )

        val drawn = GameEngine.drawForCurrentPlayer(session, 0, Random(1))

        assertEquals(blue(8), drawn?.hands?.get(0)?.last())
        assertEquals(listOf(green(5)), drawn?.discardPile)
    }

    @Test
    fun cannotDrawButCanStillPlayMatchingCard() {
        val matchingCard = red(2)
        val session = session(
            hands = listOf(listOf(matchingCard, blue(3)), listOf(green(4))),
            drawPile = emptyList(),
            discardPile = listOf(red(5))
        )

        val failedDraw = GameEngine.drawForCurrentPlayer(session, 0, Random(1))!!

        assertEquals(null, failedDraw.drawnCardIndex)
        val played = GameEngine.playCard(failedDraw, 0, 0, random = Random(2))
        assertEquals(1, played?.hands?.get(0)?.size)
    }

    @Test
    fun exhaustedDrawAndDiscardPilesEndInDrawAfterOneFullRound() {
        val session = session(
            hands = listOf(listOf(blue(2)), listOf(green(3))),
            discardPile = listOf(red(5))
        )

        val firstDraw = GameEngine.drawForCurrentPlayer(session, 0, Random(1))!!
        val afterHuman = GameEngine.passTurn(firstDraw, 0)!!
        val cpuDraw = GameEngine.drawForCurrentPlayer(afterHuman, 1, Random(1))!!
        val completed = GameEngine.passTurn(cpuDraw, 1)

        assertTrue(completed?.isDraw == true)
        assertTrue(completed?.isOver == true)
    }

    @Test
    fun pausedSessionRejectsMoves() {
        val paused = session(
            hands = listOf(listOf(red(2)), listOf(blue(3)))
        ).copy(isPaused = true)

        assertNull(GameEngine.playCard(paused, 0, 0))
        assertNull(GameEngine.drawForCurrentPlayer(paused, 0))
    }

    @Test
    fun cpuDecisionIsDeterministicAndUsesOnlyItsVisibleInputs() {
        val hand = listOf(red(GameCard.DRAW_TWO), blue(4), GameCard(CardColor.WILD, GameCard.WILD, true))
        val first = GameEngine.chooseCpuMove(
            hand, red(3), CardColor.RED, CpuDifficulty.HARD, 1, Random(4)
        )
        val second = GameEngine.chooseCpuMove(
            hand, red(3), CardColor.RED, CpuDifficulty.HARD, 1, Random(4)
        )

        assertEquals(first, second)
        assertEquals(0, first?.cardIndex)
    }

    @Test
    fun cpuDoesNotPlayWildDrawFourWhenItHasActiveColor() {
        val hand = listOf(
            GameCard(CardColor.WILD, GameCard.WILD_DRAW_FOUR, true),
            red(7)
        )

        val choice = GameEngine.chooseCpuMove(
            hand, red(3), CardColor.RED, CpuDifficulty.HARD, 5, Random(2)
        )

        assertEquals(1, choice?.cardIndex)
    }

    @Test
    fun sessionSerializationRoundTripsAndRejectsMalformedData() {
        val original = GameEngine.newGame(3, Random(7)).copy(isPaused = true)

        assertEquals(original, GameEngine.decodeSession(GameEngine.encodeSession(original)))
        assertNull(GameEngine.decodeSession("not a game state"))
    }

    private fun session(
        hands: List<List<GameCard>>,
        drawPile: List<GameCard> = listOf(green(7), yellow(8)),
        discardPile: List<GameCard> = listOf(red(5)),
        currentPlayerIndex: Int = 0,
        direction: Int = 1,
        activeColor: CardColor = CardColor.RED
    ) = GameSession(
        hands = hands,
        drawPile = drawPile,
        discardPile = discardPile,
        currentPlayerIndex = currentPlayerIndex,
        direction = direction,
        activeColor = activeColor
    )

    private fun red(value: Int) = GameCard(CardColor.RED, value)
    private fun blue(value: Int) = GameCard(CardColor.BLUE, value)
    private fun green(value: Int) = GameCard(CardColor.GREEN, value)
    private fun yellow(value: Int) = GameCard(CardColor.YELLOW, value)
}
