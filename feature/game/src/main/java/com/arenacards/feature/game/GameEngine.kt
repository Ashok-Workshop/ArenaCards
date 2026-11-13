package com.arenacards.feature.game

import kotlin.random.Random

enum class CardColor { RED, BLUE, GREEN, YELLOW, WILD }

data class GameCard(
    val color: CardColor,
    val value: Int,
    val isWild: Boolean = false
) {
    val isWildDrawFour: Boolean
        get() = isWild && value == WILD_DRAW_FOUR

    companion object {
        const val SKIP = 10
        const val REVERSE = 11
        const val DRAW_TWO = 12
        const val WILD = 13
        const val WILD_DRAW_FOUR = 14
    }
}

enum class CpuDifficulty { EASY, MEDIUM, HARD }

data class CpuDecision(
    val cardIndex: Int,
    val chosenColor: CardColor? = null
)

data class GameSession(
    val hands: List<List<GameCard>>,
    val drawPile: List<GameCard>,
    val discardPile: List<GameCard>,
    val currentPlayerIndex: Int,
    val direction: Int,
    val activeColor: CardColor,
    val winnerIndex: Int? = null,
    val isDraw: Boolean = false,
    val isPaused: Boolean = false,
    val hasDrawnThisTurn: Boolean = false,
    val drawnCardIndex: Int? = null,
    val consecutiveUnplayedTurns: Int = 0
) {
    val isOver: Boolean
        get() = winnerIndex != null || isDraw

    val topCard: GameCard
        get() = discardPile.last()
}

object GameEngine {
    private val playableColors = listOf(
        CardColor.RED,
        CardColor.BLUE,
        CardColor.GREEN,
        CardColor.YELLOW
    )

    fun createDeck(random: Random = Random.Default): List<GameCard> {
        val deck = mutableListOf<GameCard>()
        for (color in playableColors) {
            deck += GameCard(color, 0)
            for (value in 1..9) {
                repeat(2) { deck += GameCard(color, value) }
            }
            repeat(2) {
                deck += GameCard(color, GameCard.SKIP)
                deck += GameCard(color, GameCard.REVERSE)
                deck += GameCard(color, GameCard.DRAW_TWO)
            }
        }
        repeat(4) {
            deck += GameCard(CardColor.WILD, GameCard.WILD, isWild = true)
            deck += GameCard(CardColor.WILD, GameCard.WILD_DRAW_FOUR, isWild = true)
        }
        return deck.shuffled(random)
    }

    fun dealHands(
        playerCount: Int = 2,
        cardsPerPlayer: Int = 7,
        random: Random = Random.Default
    ): List<List<GameCard>> {
        require(playerCount in 2..4) { "A practice game requires two to four players." }
        require(cardsPerPlayer > 0) { "Each player must receive at least one card." }
        val deck = createDeck(random)
        require(playerCount * cardsPerPlayer < deck.size) { "Not enough cards to deal this game." }
        return List(playerCount) { playerIndex ->
            List(cardsPerPlayer) { cardIndex ->
                deck[cardIndex * playerCount + playerIndex]
            }
        }
    }

    fun newGame(
        playerCount: Int = 2,
        random: Random = Random.Default
    ): GameSession {
        require(playerCount in 2..4) { "A practice game requires two to four players." }
        val deck = createDeck(random)
        val hands = List(playerCount) { mutableListOf<GameCard>() }
        var deckIndex = 0
        repeat(INITIAL_HAND_SIZE) {
            hands.forEach { hand -> hand += deck[deckIndex++] }
        }

        val openingIndex = deck.indices.firstOrNull { index ->
            val card = deck[index]
            index >= deckIndex && !card.isWild && card.value in 0..9
        } ?: error("A standard deck must contain a number card.")
        val openingCard = deck[openingIndex]
        val remainingCards = deck.filterIndexed { index, _ ->
            index >= deckIndex && index != openingIndex
        }

        return GameSession(
            hands = hands,
            drawPile = remainingCards,
            discardPile = listOf(openingCard),
            currentPlayerIndex = 0,
            direction = 1,
            activeColor = openingCard.color
        )
    }

    fun isPlayable(topCard: GameCard, candidate: GameCard): Boolean =
        candidate.isWild || candidate.color == topCard.color || candidate.value == topCard.value

    fun isPlayable(
        topCard: GameCard,
        candidate: GameCard,
        activeColor: CardColor,
        hand: List<GameCard>
    ): Boolean {
        if (candidate.isWildDrawFour && hand.any {
                it !== candidate && !it.isWild && it.color == activeColor
            }
        ) {
            return false
        }
        return candidate.isWild || candidate.color == activeColor || candidate.value == topCard.value
    }

    fun calculateScore(cards: List<GameCard>): Int = cards.sumOf { card ->
        when {
            card.isWild -> WILD_CARD_SCORE
            card.value >= GameCard.SKIP -> ACTION_CARD_SCORE
            else -> card.value
        }
    }

    fun playCard(
        session: GameSession,
        playerIndex: Int,
        cardIndex: Int,
        chosenColor: CardColor? = null,
        random: Random = Random.Default
    ): GameSession? {
        if (!session.canAct(playerIndex) || session.isOver) return null
        val hand = session.hands[playerIndex]
        val card = hand.getOrNull(cardIndex) ?: return null
        if (session.hasDrawnThisTurn &&
            session.drawnCardIndex != null &&
            cardIndex != session.drawnCardIndex
        ) {
            return null
        }
        if (!isPlayable(session.topCard, card, session.activeColor, hand)) return null
        if (card.isWild && chosenColor !in playableColors) return null

        val updatedHands = session.hands.map { it.toMutableList() }
        updatedHands[playerIndex].removeAt(cardIndex)
        if (updatedHands[playerIndex].isEmpty()) {
            return session.copy(
                hands = updatedHands,
                discardPile = session.discardPile + card,
                activeColor = chosenColor ?: card.color,
                winnerIndex = playerIndex,
                hasDrawnThisTurn = false,
                drawnCardIndex = null,
                consecutiveUnplayedTurns = 0
            )
        }

        var direction = session.direction
        var nextPlayer = session.nextPlayer(playerIndex)
        var updatedDrawPile = session.drawPile
        var updatedDiscardPile = session.discardPile + card
        var nextActiveColor = chosenColor ?: card.color
        var noPlayTurns = 0

        when (card.value) {
            GameCard.SKIP -> nextPlayer = session.nextPlayer(nextPlayer)
            GameCard.REVERSE -> {
                direction *= -1
                nextPlayer = if (session.hands.size == 2) {
                    playerIndex
                } else {
                    (playerIndex + direction + session.hands.size) % session.hands.size
                }
            }
            GameCard.DRAW_TWO, GameCard.WILD_DRAW_FOUR -> {
                val target = nextPlayer
                val penalty = if (card.value == GameCard.DRAW_TWO) 2 else 4
                val drawResult = drawCards(
                    hands = updatedHands,
                    drawPile = updatedDrawPile,
                    discardPile = updatedDiscardPile,
                    playerIndex = target,
                    count = penalty,
                    random = random
                )
                updatedDrawPile = drawResult.drawPile
                updatedDiscardPile = drawResult.discardPile
                nextPlayer = session.nextPlayer(target, direction)
            }
        }

        return session.copy(
            hands = updatedHands,
            drawPile = updatedDrawPile,
            discardPile = updatedDiscardPile,
            currentPlayerIndex = nextPlayer,
            direction = direction,
            activeColor = nextActiveColor,
            hasDrawnThisTurn = false,
            drawnCardIndex = null,
            consecutiveUnplayedTurns = noPlayTurns
        )
    }

    fun drawForCurrentPlayer(
        session: GameSession,
        playerIndex: Int,
        random: Random = Random.Default
    ): GameSession? {
        if (!session.canAct(playerIndex) || session.isOver || session.hasDrawnThisTurn) return null
        val drawResult = drawCards(
            hands = session.hands.map { it.toMutableList() },
            drawPile = session.drawPile,
            discardPile = session.discardPile,
            playerIndex = playerIndex,
            count = 1,
            random = random
        )
        val addedCardIndex = drawResult.hands[playerIndex].lastIndex.takeIf {
            drawResult.hands[playerIndex].size > session.hands[playerIndex].size
        }
        return session.copy(
            hands = drawResult.hands,
            drawPile = drawResult.drawPile,
            discardPile = drawResult.discardPile,
            hasDrawnThisTurn = true,
            drawnCardIndex = addedCardIndex
        )
    }

    fun passTurn(session: GameSession, playerIndex: Int): GameSession? {
        if (!session.canAct(playerIndex) || session.isOver || !session.hasDrawnThisTurn) return null
        val nextPlayer = session.nextPlayer(playerIndex)
        val unplayedTurns = session.consecutiveUnplayedTurns + 1
        val noCardsCanBeDrawn = session.drawPile.isEmpty() && session.discardPile.size <= 1
        val stalemate = unplayedTurns >= session.hands.size && noCardsCanBeDrawn
        return session.copy(
            currentPlayerIndex = nextPlayer,
            hasDrawnThisTurn = false,
            drawnCardIndex = null,
            consecutiveUnplayedTurns = unplayedTurns,
            isDraw = stalemate
        )
    }

    fun chooseCpuMove(
        hand: List<GameCard>,
        topCard: GameCard,
        activeColor: CardColor,
        difficulty: CpuDifficulty,
        nextPlayerCardCount: Int,
        random: Random = Random.Default
    ): CpuDecision? {
        val playable = hand.indices.filter { index ->
            isPlayable(topCard, hand[index], activeColor, hand)
        }
        if (playable.isEmpty()) return null

        val chosenIndex = when (difficulty) {
            CpuDifficulty.EASY -> playable.random(random)
            CpuDifficulty.MEDIUM -> playable.maxBy { index ->
                cpuCardPriority(hand[index], nextPlayerCardCount, hand, activeColor)
            }
            CpuDifficulty.HARD -> playable.maxWith(
                compareBy<Int> { index ->
                    cpuCardPriority(hand[index], nextPlayerCardCount, hand, activeColor)
                }.thenBy { index ->
                    hand.count { it.color == hand[index].color && !it.isWild }
                }
            )
        }
        val card = hand[chosenIndex]
        val chosenColor = if (card.isWild) {
            playableColors.maxBy { color -> hand.count { it.color == color && !it.isWild } }
        } else {
            null
        }
        return CpuDecision(chosenIndex, chosenColor)
    }

    fun encodeSession(session: GameSession): String = listOf(
        session.currentPlayerIndex.toString(),
        session.direction.toString(),
        session.activeColor.name,
        session.winnerIndex?.toString() ?: "-1",
        session.isDraw.toString(),
        session.isPaused.toString(),
        session.hasDrawnThisTurn.toString(),
        session.drawnCardIndex?.toString() ?: "-1",
        session.consecutiveUnplayedTurns.toString(),
        session.hands.joinToString(HAND_SEPARATOR) { hand -> hand.joinToString(CARD_SEPARATOR, transform = ::encodeCard) },
        session.drawPile.joinToString(CARD_SEPARATOR, transform = ::encodeCard),
        session.discardPile.joinToString(CARD_SEPARATOR, transform = ::encodeCard)
    ).joinToString(SESSION_SEPARATOR)

    fun decodeSession(encoded: String): GameSession? {
        val parts = encoded.split(SESSION_SEPARATOR)
        if (parts.size != SESSION_PART_COUNT) return null
        val currentPlayer = parts[0].toIntOrNull() ?: return null
        val direction = parts[1].toIntOrNull() ?: return null
        val color = parts[2].toEnumOrNull<CardColor>() ?: return null
        val winner = parts[3].toIntOrNull()?.takeIf { it >= 0 }
        val isDraw = parts[4].toBooleanStrictOrNull() ?: return null
        val isPaused = parts[5].toBooleanStrictOrNull() ?: return null
        val hasDrawn = parts[6].toBooleanStrictOrNull() ?: return null
        val drawnIndex = parts[7].toIntOrNull()?.takeIf { it >= 0 }
        val noPlayTurns = parts[8].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val hands = parts[9].split(HAND_SEPARATOR).map { hand -> decodeCards(hand) ?: return null }
        val drawPile = decodeCards(parts[10]) ?: return null
        val discardPile = decodeCards(parts[11]) ?: return null

        if (hands.size !in 2..4 || currentPlayer !in hands.indices ||
            direction !in setOf(-1, 1) || discardPile.isEmpty() ||
            winner?.let { it !in hands.indices } == true ||
            drawnIndex?.let { it !in hands[currentPlayer].indices } == true
        ) {
            return null
        }
        return GameSession(
            hands = hands,
            drawPile = drawPile,
            discardPile = discardPile,
            currentPlayerIndex = currentPlayer,
            direction = direction,
            activeColor = color,
            winnerIndex = winner,
            isDraw = isDraw,
            isPaused = isPaused,
            hasDrawnThisTurn = hasDrawn,
            drawnCardIndex = drawnIndex,
            consecutiveUnplayedTurns = noPlayTurns
        )
    }

    private fun cpuCardPriority(
        card: GameCard,
        nextPlayerCardCount: Int,
        hand: List<GameCard>,
        activeColor: CardColor
    ): Int {
        val actionPriority = when (card.value) {
            GameCard.SKIP, GameCard.REVERSE -> 2
            GameCard.DRAW_TWO -> 4
            GameCard.WILD_DRAW_FOUR -> 5
            GameCard.WILD -> 3
            else -> 0
        }
        val urgency = if (nextPlayerCardCount <= 2 && actionPriority > 0) 4 else 0
        val colorContinuity = hand.count { it.color == card.color && !it.isWild }
        val colorSwitchPenalty = if (!card.isWild && card.color != activeColor) 1 else 0
        return actionPriority + urgency + colorContinuity - colorSwitchPenalty
    }

    private fun drawCards(
        hands: List<MutableList<GameCard>>,
        drawPile: List<GameCard>,
        discardPile: List<GameCard>,
        playerIndex: Int,
        count: Int,
        random: Random
    ): DrawResult {
        val cardsInDrawPile = drawPile.toMutableList()
        var cardsInDiscard = discardPile.toMutableList()
        repeat(count) {
            if (cardsInDrawPile.isEmpty() && cardsInDiscard.size > 1) {
                val topCard = cardsInDiscard.removeAt(cardsInDiscard.lastIndex)
                cardsInDrawPile += cardsInDiscard.shuffled(random)
                cardsInDiscard = mutableListOf(topCard)
            }
            cardsInDrawPile.removeFirstOrNull()?.let(hands[playerIndex]::add)
        }
        return DrawResult(hands, cardsInDrawPile, cardsInDiscard)
    }

    private fun GameSession.canAct(playerIndex: Int): Boolean =
        !isPaused && currentPlayerIndex == playerIndex && playerIndex in hands.indices

    private fun GameSession.nextPlayer(from: Int, turnDirection: Int = direction): Int =
        (from + turnDirection + hands.size) % hands.size

    private data class DrawResult(
        val hands: List<MutableList<GameCard>>,
        val drawPile: List<GameCard>,
        val discardPile: List<GameCard>
    )

    private fun encodeCard(card: GameCard): String =
        "${card.color.name},${card.value},${card.isWild}"

    private fun decodeCards(encoded: String): List<GameCard>? {
        if (encoded.isEmpty()) return emptyList()
        return encoded.split(CARD_SEPARATOR).map { token ->
            val fields = token.split(',')
            if (fields.size != CARD_PART_COUNT) return null
            val color = fields[0].toEnumOrNull<CardColor>() ?: return null
            val value = fields[1].toIntOrNull() ?: return null
            val wild = fields[2].toBooleanStrictOrNull() ?: return null
            if (value !in 0..GameCard.WILD_DRAW_FOUR ||
                wild != (color == CardColor.WILD) ||
                (wild && value !in GameCard.WILD..GameCard.WILD_DRAW_FOUR) ||
                (!wild && value > GameCard.DRAW_TWO)
            ) {
                return null
            }
            GameCard(color, value, wild)
        }
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        enumValues<T>().firstOrNull { it.name == this }

    private const val INITIAL_HAND_SIZE = 7
    private const val ACTION_CARD_SCORE = 20
    private const val WILD_CARD_SCORE = 50
    private const val SESSION_PART_COUNT = 12
    private const val CARD_PART_COUNT = 3
    private const val SESSION_SEPARATOR = "|"
    private const val HAND_SEPARATOR = ";"
    private const val CARD_SEPARATOR = ":"
}
