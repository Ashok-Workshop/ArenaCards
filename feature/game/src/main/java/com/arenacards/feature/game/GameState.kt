package com.arenacards.feature.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OpponentUiState(
    val name: String,
    val cardCount: Int,
    val score: Int,
    val isActive: Boolean
)

data class GameUiState(
    val playerCards: List<GameCard> = emptyList(),
    val playableCardIndices: Set<Int> = emptySet(),
    val opponents: List<OpponentUiState> = emptyList(),
    val topCard: GameCard = GameCard(CardColor.RED, 5),
    val drawPileCount: Int = 0,
    val selectedCardIndex: Int? = null,
    val currentPlayerIndex: Int = 0,
    val score: Int = 0,
    val status: String = "Your turn",
    val winner: String? = null,
    val chooseWildColor: Boolean = false
)

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var playerCount = 2
    private var hands: List<MutableList<GameCard>> = emptyList()
    private var drawPile: MutableList<GameCard> = mutableListOf()
    private var topCard = GameCard(CardColor.RED, 5)
    private var selectedCardIndex: Int? = null
    private var currentPlayerIndex = 0
    private var winner: String? = null
    private var status = "Your turn"
    private var chooseWildColor = false

    init {
        startNewGame(2)
    }

    fun startNewGame(requestedPlayerCount: Int) {
        playerCount = requestedPlayerCount.coerceIn(2, 4)
        val deck = GameEngine.createDeck()
        hands = GameEngine.dealHands(playerCount).map { it.toMutableList() }
        val firstDiscardIndex = playerCount * INITIAL_HAND_SIZE
        val openingCard = deck[firstDiscardIndex]
        topCard = if (openingCard.isWild) openingCard.copy(color = CardColor.RED) else openingCard
        drawPile = deck.drop(firstDiscardIndex + 1).toMutableList()
        selectedCardIndex = null
        currentPlayerIndex = 0
        winner = null
        status = "Your turn — play a matching color or number."
        chooseWildColor = false
        publishState()
    }

    fun selectCard(index: Int) {
        if (winner != null || currentPlayerIndex != 0) return
        val card = hands.firstOrNull()?.getOrNull(index) ?: return
        if (!GameEngine.isPlayable(topCard, card)) {
            status = "That card does not match the top card."
            publishState()
            return
        }
        selectedCardIndex = index
        status = if (card.isWild) {
            "Choose a color for your wild card."
        } else {
            "Ready to play ${cardName(card)}."
        }
        publishState()
    }

    fun playSelectedCard() {
        val index = selectedCardIndex ?: return
        val card = hands.firstOrNull()?.getOrNull(index) ?: return
        if (card.isWild) {
            chooseWildColor = true
            status = "Choose a color for your wild card."
            publishState()
            return
        }
        playCard(index, card)
    }

    fun chooseWildColor(color: CardColor) {
        if (!chooseWildColor || color == CardColor.WILD) return
        val index = selectedCardIndex ?: return
        val card = hands.firstOrNull()?.getOrNull(index) ?: return
        playCard(index, card.copy(color = color))
    }

    fun cancelWildColor() {
        if (!chooseWildColor) return
        chooseWildColor = false
        status = "Wild card remains selected. Choose a color to play it."
        publishState()
    }

    fun drawCard() {
        if (winner != null || currentPlayerIndex != 0) return
        val card = drawPile.removeFirstOrNull()
        if (card == null) {
            status = "The draw pile is empty. Start a new practice match."
            publishState()
            return
        }
        hands[0].add(card)
        selectedCardIndex = null
        status = "You drew a card. The CPU seats take their turns."
        currentPlayerIndex = 1 % playerCount
        advancePastOpponents()
        publishState()
    }

    private fun playCard(index: Int, playedCard: GameCard) {
        hands[0].removeAt(index)
        topCard = playedCard
        selectedCardIndex = null
        chooseWildColor = false

        if (hands[0].isEmpty()) {
            winner = "You win"
            status = "You cleared your hand."
            publishState()
            return
        }
        status = if (playedCard.value >= 10) {
            "Card played. Special-card effects are not part of this preview."
        } else {
            "Card played. CPU seats take their turns."
        }
        currentPlayerIndex = 1 % playerCount
        advancePastOpponents()
        publishState()
    }

    private fun advancePastOpponents() {
        while (winner == null && currentPlayerIndex != 0) {
            val hand = hands[currentPlayerIndex]
            val playableIndex = hand.indexOfFirst { GameEngine.isPlayable(topCard, it) }
            if (playableIndex >= 0) {
                val card = hand.removeAt(playableIndex)
                topCard = if (card.isWild) {
                    card.copy(color = chooseCpuColor(hand))
                } else {
                    card
                }
                if (hand.isEmpty()) {
                    winner = "CPU ${currentPlayerIndex} wins"
                    status = "$winner cleared their hand."
                    return
                }
            } else {
                drawPile.removeFirstOrNull()?.let(hand::add)
            }
            currentPlayerIndex = (currentPlayerIndex + 1) % playerCount
        }
    }

    private fun chooseCpuColor(hand: List<GameCard>): CardColor {
        return hand
            .asSequence()
            .map { it.color }
            .firstOrNull { it != CardColor.WILD }
            ?: CardColor.BLUE
    }

    private fun publishState() {
        val playerHand = hands.firstOrNull().orEmpty()
        _uiState.value = GameUiState(
            playerCards = playerHand.toList(),
            playableCardIndices = playerHand.mapIndexedNotNull { index, card ->
                index.takeIf { winner == null && currentPlayerIndex == 0 && GameEngine.isPlayable(topCard, card) }
            }.toSet(),
            opponents = hands.drop(1).mapIndexed { index, hand ->
                OpponentUiState(
                    name = "CPU ${index + 1}",
                    cardCount = hand.size,
                    score = GameEngine.calculateScore(hand),
                    isActive = currentPlayerIndex == index + 1
                )
            },
            topCard = topCard,
            drawPileCount = drawPile.size,
            selectedCardIndex = selectedCardIndex,
            currentPlayerIndex = currentPlayerIndex,
            score = GameEngine.calculateScore(playerHand),
            status = status,
            winner = winner,
            chooseWildColor = chooseWildColor
        )
    }

    private fun cardName(card: GameCard): String = when (card.value) {
        10 -> "Skip"
        11 -> "Reverse"
        12 -> "Draw two"
        13 -> "Wild"
        14 -> "Wild draw four"
        else -> "${card.color.name.lowercase()} ${card.value}"
    }

    private companion object {
        const val INITIAL_HAND_SIZE = 7
    }
}
