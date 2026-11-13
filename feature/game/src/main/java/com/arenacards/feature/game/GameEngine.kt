package com.arenacards.feature.game

import kotlin.random.Random

enum class CardColor { RED, BLUE, GREEN, YELLOW, WILD }

data class GameCard(
    val color: CardColor,
    val value: Int,
    val isWild: Boolean = false
)

object GameEngine {
    fun createDeck(): List<GameCard> {
        val deck = mutableListOf<GameCard>()
        for (color in CardColor.values().filter { it != CardColor.WILD }) {
            deck += GameCard(color, 0)
            for (value in 1..9) {
                repeat(2) {
                    deck += GameCard(color, value)
                }
            }
            repeat(2) {
                deck += GameCard(color, 10)
                deck += GameCard(color, 11)
                deck += GameCard(color, 12)
            }
        }
        repeat(4) {
            deck += GameCard(CardColor.WILD, 13, isWild = true)
        }
        repeat(4) {
            deck += GameCard(CardColor.WILD, 14, isWild = true)
        }
        return deck.shuffled(Random(42))
    }

    fun dealHands(playerCount: Int = 2, cardsPerPlayer: Int = 7): List<List<GameCard>> {
        val deck = createDeck()
        val hands = MutableList(playerCount) { mutableListOf<GameCard>() }
        for (index in 0 until playerCount * cardsPerPlayer) {
            hands[index % playerCount].add(deck[index])
        }
        return hands
    }

    fun isPlayable(topCard: GameCard, candidate: GameCard): Boolean {
        return candidate.isWild ||
            candidate.color == topCard.color ||
            candidate.value == topCard.value
    }

    fun calculateScore(cards: List<GameCard>): Int {
        return cards.sumOf { card ->
            when {
                card.isWild -> 50
                else -> card.value
            }
        }
    }
}
