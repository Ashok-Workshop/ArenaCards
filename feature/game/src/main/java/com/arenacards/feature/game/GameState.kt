package com.arenacards.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class OpponentUiState(
    val name: String,
    val cardCount: Int,
    val isActive: Boolean
)

data class GameUiState(
    val playerCards: List<GameCard> = emptyList(),
    val playableCardIndices: Set<Int> = emptySet(),
    val opponents: List<OpponentUiState> = emptyList(),
    val topCard: GameCard = GameCard(CardColor.RED, 5),
    val activeColor: CardColor = CardColor.RED,
    val drawPileCount: Int = 0,
    val selectedCardIndex: Int? = null,
    val currentPlayerIndex: Int = 0,
    val turnDirection: Int = 1,
    val score: Int = 0,
    val status: String = "Your turn",
    val winner: String? = null,
    val isDraw: Boolean = false,
    val isPaused: Boolean = false,
    val hasDrawnThisTurn: Boolean = false,
    val difficulty: CpuDifficulty = CpuDifficulty.MEDIUM,
    val chooseWildColor: Boolean = false
)

class GameViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val random: Random = Random.Default
) : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var playerCount = 2
    private var difficulty = savedStateHandle.get<String>(DIFFICULTY_KEY)
        ?.let { name -> CpuDifficulty.entries.firstOrNull { it.name == name } }
        ?: CpuDifficulty.MEDIUM
    private var session = savedStateHandle.get<String>(SESSION_KEY)
        ?.let(GameEngine::decodeSession)
        ?: GameEngine.newGame(playerCount, random)
    private var selectedCardIndex: Int? = null
    private var chooseWildColor = false
    private var status = "Your turn — play a matching color or number."

    init {
        playerCount = session.hands.size
        if (savedStateHandle.contains(SESSION_KEY)) status = "Practice match restored."
        publishState()
    }

    fun ensurePlayerCount(requestedPlayerCount: Int) {
        val validCount = requestedPlayerCount.coerceIn(2, 4)
        if (validCount != playerCount || session.hands.size != validCount) startNewGame(validCount)
    }

    fun startNewGame(requestedPlayerCount: Int = playerCount) {
        playerCount = requestedPlayerCount.coerceIn(2, 4)
        session = GameEngine.newGame(playerCount, random)
        selectedCardIndex = null
        chooseWildColor = false
        status = "Your turn — play a matching color or number."
        publishState()
    }

    fun setDifficulty(value: CpuDifficulty) {
        difficulty = value
        savedStateHandle[DIFFICULTY_KEY] = value.name
        publishState()
    }

    fun pauseGame() {
        if (session.isOver || session.isPaused) return
        session = session.copy(isPaused = true)
        status = "Match paused."
        publishState()
    }

    fun resumeGame() {
        if (!session.isPaused) return
        session = session.copy(isPaused = false)
        status = if (session.currentPlayerIndex == HUMAN_PLAYER) {
            "Your turn — play a matching color or number."
        } else {
            "CPU ${session.currentPlayerIndex} is thinking."
        }
        advanceCpuTurns()
        publishState()
    }

    fun selectCard(index: Int) {
        if (session.isOver || session.isPaused || session.currentPlayerIndex != HUMAN_PLAYER) return
        val card = session.hands[HUMAN_PLAYER].getOrNull(index) ?: return
        if (index !in playableCardIndices()) {
            status = if (card.isWildDrawFour &&
                session.hands[HUMAN_PLAYER].any { !it.isWild && it.color == session.activeColor }
            ) {
                "Wild Draw Four can only be played when you have no card of the active color."
            } else {
                "That card does not match the active color or top card."
            }
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
        val card = session.hands[HUMAN_PLAYER].getOrNull(index) ?: return
        if (card.isWild) {
            chooseWildColor = true
            status = "Choose a color for your wild card."
            publishState()
            return
        }
        playCard(index, null)
    }

    fun chooseWildColor(color: CardColor) {
        if (!chooseWildColor || color == CardColor.WILD) return
        val index = selectedCardIndex ?: return
        chooseWildColor = false
        playCard(index, color)
    }

    fun cancelWildColor() {
        if (!chooseWildColor) return
        chooseWildColor = false
        status = "Wild card remains selected. Choose a color to play it."
        publishState()
    }

    fun drawCard() {
        if (session.isOver || session.isPaused || session.currentPlayerIndex != HUMAN_PLAYER) return
        val drawn = GameEngine.drawForCurrentPlayer(session, HUMAN_PLAYER, random) ?: return
        session = drawn
        selectedCardIndex = null
        status = if (drawn.drawnCardIndex == null) {
            "No cards are available to draw. End your turn."
        } else {
            "You drew a card. Play it if it matches, or end your turn."
        }
        publishState()
    }

    fun endTurn() {
        val advanced = GameEngine.passTurn(session, HUMAN_PLAYER) ?: return
        session = advanced
        selectedCardIndex = null
        status = if (session.isDraw) {
            "The draw pile and discard pile are exhausted. The round is a draw."
        } else {
            "CPU seats take their turns."
        }
        advanceCpuTurns()
        publishState()
    }

    private fun playCard(index: Int, wildColor: CardColor?) {
        val card = session.hands[HUMAN_PLAYER].getOrNull(index) ?: return
        val updated = GameEngine.playCard(session, HUMAN_PLAYER, index, wildColor, random)
        if (updated == null) {
            status = "That move is no longer valid. Choose another card."
            selectedCardIndex = null
            publishState()
            return
        }
        session = updated
        selectedCardIndex = null
        status = if (session.winnerIndex == HUMAN_PLAYER) {
            "You cleared your hand. You win!"
        } else if (session.winnerIndex != null) {
            "CPU ${session.winnerIndex} cleared their hand."
        } else {
            effectMessage(card, wildColor)
        }
        advanceCpuTurns()
        publishState()
    }

    private fun advanceCpuTurns() {
        while (!session.isOver && !session.isPaused &&
            session.currentPlayerIndex != HUMAN_PLAYER
        ) {
            val cpuIndex = session.currentPlayerIndex
            val hand = session.hands[cpuIndex]
            val nextPlayer = (cpuIndex + session.direction + session.hands.size) % session.hands.size
            val decision = GameEngine.chooseCpuMove(
                hand = hand,
                topCard = session.topCard,
                activeColor = session.activeColor,
                difficulty = difficulty,
                nextPlayerCardCount = session.hands[nextPlayer].size,
                random = random
            )
            if (decision != null) {
                val played = GameEngine.playCard(
                    session = session,
                    playerIndex = cpuIndex,
                    cardIndex = decision.cardIndex,
                    chosenColor = decision.chosenColor,
                    random = random
                )
                if (played == null) break
                session = played
                status = if (session.winnerIndex == cpuIndex) {
                    "CPU ${cpuIndex} wins the round."
                } else {
                    "CPU ${cpuIndex} played ${cardName(hand[decision.cardIndex])}."
                }
            } else {
                session = GameEngine.drawForCurrentPlayer(session, cpuIndex, random) ?: break
                val drawnIndex = session.drawnCardIndex
                val canPlayDrawnCard = drawnIndex != null &&
                    GameEngine.isPlayable(
                        session.topCard,
                        session.hands[cpuIndex][drawnIndex],
                        session.activeColor,
                        session.hands[cpuIndex]
                    )
                if (canPlayDrawnCard && drawnIndex != null) {
                    val drawnCard = session.hands[cpuIndex][drawnIndex]
                    val drawDecision = GameEngine.chooseCpuMove(
                        hand = listOf(drawnCard),
                        topCard = session.topCard,
                        activeColor = session.activeColor,
                        difficulty = difficulty,
                        nextPlayerCardCount = session.hands[nextPlayer].size,
                        random = random
                    )
                    val played = drawDecision?.let {
                        GameEngine.playCard(
                            session = session,
                            playerIndex = cpuIndex,
                            cardIndex = drawnIndex,
                            chosenColor = it.chosenColor,
                            random = random
                        )
                    }
                    if (played != null) {
                        session = played
                        status = "CPU ${cpuIndex} drew and played ${cardName(drawnCard)}."
                        continue
                    }
                }
                session = GameEngine.passTurn(session, cpuIndex) ?: break
                status = "CPU ${cpuIndex} drew and passed."
            }
        }
    }

    private fun playableCardIndices(): Set<Int> {
        val hand = session.hands[HUMAN_PLAYER]
        val candidates = if (session.hasDrawnThisTurn) {
            session.drawnCardIndex?.let(::listOf) ?: hand.indices.toList()
        } else {
            hand.indices.toList()
        }
        return candidates.filterTo(mutableSetOf()) { index ->
            val card = hand[index]
            GameEngine.isPlayable(session.topCard, card, session.activeColor, hand)
        }
    }

    private fun publishState() {
        val hand = session.hands[HUMAN_PLAYER]
        val opponents = session.hands.drop(1).mapIndexed { index, opponentHand ->
            OpponentUiState(
                name = "CPU ${index + 1}",
                cardCount = opponentHand.size,
                isActive = session.currentPlayerIndex == index + 1
            )
        }
        savedStateHandle[SESSION_KEY] = GameEngine.encodeSession(session)
        _uiState.value = GameUiState(
            playerCards = hand,
            playableCardIndices = playableCardIndices(),
            opponents = opponents,
            topCard = session.topCard,
            activeColor = session.activeColor,
            drawPileCount = session.drawPile.size,
            selectedCardIndex = selectedCardIndex,
            currentPlayerIndex = session.currentPlayerIndex,
            turnDirection = session.direction,
            score = GameEngine.calculateScore(hand),
            status = status,
            winner = session.winnerIndex?.let { index ->
                if (index == HUMAN_PLAYER) "You win" else "CPU ${index} wins"
            },
            isDraw = session.isDraw,
            isPaused = session.isPaused,
            hasDrawnThisTurn = session.hasDrawnThisTurn,
            difficulty = difficulty,
            chooseWildColor = chooseWildColor
        )
    }

    private fun effectMessage(card: GameCard, wildColor: CardColor?): String = when (card.value) {
        GameCard.SKIP -> "Skip played. The next player loses a turn."
        GameCard.REVERSE -> "Direction reversed."
        GameCard.DRAW_TWO -> "Draw Two played. The next player draws two and misses a turn."
        GameCard.WILD -> "Wild played. Active color: ${wildColor?.displayName()}."
        GameCard.WILD_DRAW_FOUR ->
            "Wild Draw Four played. The next player draws four and misses a turn."
        else -> "Card played. CPU seats take their turns."
    }

    private fun cardName(card: GameCard): String = when (card.value) {
        GameCard.SKIP -> "Skip"
        GameCard.REVERSE -> "Reverse"
        GameCard.DRAW_TWO -> "Draw Two"
        GameCard.WILD -> "Wild"
        GameCard.WILD_DRAW_FOUR -> "Wild Draw Four"
        else -> "${card.color.displayName()} ${card.value}"
    }

    private fun CardColor.displayName(): String =
        name.lowercase().replaceFirstChar { it.uppercase() }

    private companion object {
        const val HUMAN_PLAYER = 0
        const val SESSION_KEY = "offline_game_session"
        const val DIFFICULTY_KEY = "offline_game_cpu_difficulty"
    }
}
