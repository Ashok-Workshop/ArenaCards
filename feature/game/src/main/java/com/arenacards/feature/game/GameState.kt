package com.arenacards.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

data class OpponentUiState(
    val name: String,
    val cardCount: Int,
    val isActive: Boolean
)

enum class GameFeedback {
    CARD_SELECTED,
    CARD_PLAYED,
    CARD_DRAWN,
    SPECIAL_CARD,
    CHALLENGE_SUCCESS,
    CHALLENGE_FAILED,
    TURN_CHANGED,
    MATCH_STARTED,
    VICTORY,
    DEFEAT
}

data class GameUiState(
    val matchId: String = "",
    val matchDurationMillis: Long = 0L,
    val statisticsSaveError: String? = null,
    val feedbackSequence: Long = 0L,
    val feedbackEvent: GameFeedback? = null,
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
    val chooseWildColor: Boolean = false,
    val wildDrawFourChallengeAvailable: Boolean = false,
    val pendingUnoCallDeadlineMillis: Long? = null,
    val canAnnounceUno: Boolean = false
)

class GameViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val statisticsRepository: PracticeStatisticsRepository
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) :
        this(savedStateHandle, NoPracticeStatisticsRepository)

    internal constructor(savedStateHandle: SavedStateHandle, random: Random) :
        this(savedStateHandle, NoPracticeStatisticsRepository) {
        this.random = random
        if (!hasSavedSession) {
            session = GameEngine.newGame(playerCount, random)
            publishState()
        }
    }

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var random: Random = Random.Default
    private var playerCount = 2
    private var difficulty = savedStateHandle.get<String>(DIFFICULTY_KEY)
        ?.let { name -> CpuDifficulty.entries.firstOrNull { it.name == name } }
        ?: CpuDifficulty.MEDIUM
    private val hasSavedSession = savedStateHandle.contains(SESSION_KEY)
    private var session = savedStateHandle.get<String>(SESSION_KEY)
        ?.let(GameEngine::decodeSession)
        ?: GameEngine.newGame(playerCount, random)
    private var selectedCardIndex: Int? = null
    private var chooseWildColor = false
    private var status = "Your turn — play a matching color or number."
    private var feedbackSequence = 0L
    private var pendingFeedback: GameFeedback? = null
    private var matchId = savedStateHandle.get<String>(MATCH_ID_KEY)
        ?: UUID.randomUUID().toString().also { savedStateHandle[MATCH_ID_KEY] = it }
    private var matchStartedAtMillis = savedStateHandle.get<Long>(MATCH_STARTED_AT_KEY)
        ?: System.currentTimeMillis().also { savedStateHandle[MATCH_STARTED_AT_KEY] = it }
    private var recordingMatchId: String? = null

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
        matchId = UUID.randomUUID().toString()
        matchStartedAtMillis = System.currentTimeMillis()
        savedStateHandle[MATCH_ID_KEY] = matchId
        savedStateHandle[MATCH_STARTED_AT_KEY] = matchStartedAtMillis
        selectedCardIndex = null
        chooseWildColor = false
        status = "Your turn — play a matching color or number."
        pendingFeedback = GameFeedback.MATCH_STARTED
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
        pendingFeedback = GameFeedback.CARD_SELECTED
        status = if (card.isWildDrawFour &&
            !GameEngine.isWildDrawFourLegal(session.activeColor, session.hands[HUMAN_PLAYER])
        ) {
            "You may play this Wild Draw Four, but the next player can challenge it."
        } else if (card.isWild) {
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
        if (drawn.drawnCardIndex != null) pendingFeedback = GameFeedback.CARD_DRAWN
        status = if (drawn.drawnCardIndex == null) {
            "No cards are available to draw. End your turn."
        } else {
            "You drew a card. Play it if it matches, or end your turn."
        }
        publishState()
    }

    fun announceUno() {
        val announced = GameEngine.announceUno(session, HUMAN_PLAYER) ?: return
        session = announced
        status = "UNO! Your last card is announced."
        advanceCpuTurns()
        publishState()
    }

    fun catchMissedUno() {
        val offenderIndex = session.pendingUnoCall?.offenderIndex ?: return
        val catcherIndex = session.hands.indices.firstOrNull { it != offenderIndex } ?: return
        val originalHandSize = session.hands[offenderIndex].size
        val caught = GameEngine.catchMissedUno(
            session = session,
            catcherIndex = catcherIndex,
            nowMillis = session.pendingUnoCall?.deadlineMillis ?: return,
            random = random
        ) ?: return
        val penaltyCards = caught.hands[offenderIndex].size - originalHandSize
        session = caught
        status = "Missed UNO caught. You draw $penaltyCards cards."
        advanceCpuTurns()
        pendingFeedback = GameFeedback.CARD_DRAWN
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
        pendingFeedback = GameFeedback.TURN_CHANGED
        publishState()
    }

    fun respondToWildDrawFourChallenge(challenge: Boolean) {
        val offenderIndex = session.pendingWildDrawFourChallenge?.offenderIndex ?: return
        val resolved = GameEngine.resolveWildDrawFourChallenge(
            session = session,
            playerIndex = HUMAN_PLAYER,
            challenge = challenge,
            random = random
        ) ?: return
        session = resolved
        status = when {
            challenge && resolved.currentPlayerIndex == HUMAN_PLAYER ->
                "Challenge successful. CPU $offenderIndex draws four. Your turn."
            challenge && resolved.winnerIndex != null ->
                "Challenge failed. You drew six; CPU ${resolved.winnerIndex} wins."
            challenge -> "Challenge failed. You drew six cards and lose your turn."
            resolved.winnerIndex != null ->
                "You drew four cards. CPU ${resolved.winnerIndex} wins."
            else -> "You drew four cards and lose your turn."
        }
        advanceCpuTurns()
        pendingFeedback = when {
            session.winnerIndex == HUMAN_PLAYER -> GameFeedback.VICTORY
            session.winnerIndex != null -> GameFeedback.DEFEAT
            challenge && resolved.currentPlayerIndex == HUMAN_PLAYER ->
                GameFeedback.CHALLENGE_SUCCESS
            challenge -> GameFeedback.CHALLENGE_FAILED
            else -> GameFeedback.CARD_DRAWN
        }
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
        if (session.pendingUnoCall?.offenderIndex == HUMAN_PLAYER) {
            status = "Tap UNO! within 10 seconds so the next player cannot catch the missed call."
            pendingFeedback = GameFeedback.SPECIAL_CARD
            publishState()
            return
        }
        status = if (session.winnerIndex == HUMAN_PLAYER) {
            "You cleared your hand. You win!"
        } else if (session.winnerIndex != null) {
            "CPU ${session.winnerIndex} cleared their hand."
        } else {
            effectMessage(card, wildColor)
        }
        advanceCpuTurns()
        pendingFeedback = when {
            session.winnerIndex == HUMAN_PLAYER -> GameFeedback.VICTORY
            session.winnerIndex != null -> GameFeedback.DEFEAT
            card.value >= GameCard.SKIP -> GameFeedback.SPECIAL_CARD
            else -> GameFeedback.CARD_PLAYED
        }
        publishState()
    }

    private fun advanceCpuTurns() {
        while (!session.isOver && !session.isPaused &&
            session.currentPlayerIndex != HUMAN_PLAYER && session.pendingUnoCall == null
        ) {
            val cpuIndex = session.currentPlayerIndex
            if (session.pendingWildDrawFourChallenge != null) {
                val challenged = GameEngine.shouldCpuChallenge(difficulty, random)
                session = GameEngine.resolveWildDrawFourChallenge(
                    session = session,
                    playerIndex = cpuIndex,
                    challenge = challenged,
                    random = random
                ) ?: break
                status = if (challenged) {
                    if (session.currentPlayerIndex == cpuIndex) {
                        "CPU ${cpuIndex} challenged successfully. Your turn."
                    } else {
                        "CPU ${cpuIndex} challenged and drew six cards."
                    }
                } else {
                    "CPU ${cpuIndex} accepted the Wild Draw Four and drew four cards."
                }
                continue
            }
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
                val calledUno = session.pendingUnoCall?.offenderIndex == cpuIndex
                if (calledUno) {
                    session = GameEngine.announceUno(session, cpuIndex) ?: session
                }
                status = when {
                    session.winnerIndex == cpuIndex -> "CPU ${cpuIndex} wins the round."
                    calledUno -> "CPU ${cpuIndex} called UNO."
                    else -> "CPU ${cpuIndex} played ${cardName(hand[decision.cardIndex])}."
                }
            } else {
                session = GameEngine.drawForCurrentPlayer(session, cpuIndex, random) ?: break
                val drawnIndex = session.drawnCardIndex
                val canPlayDrawnCard = drawnIndex != null &&
                    GameEngine.isPlayable(
                        session.topCard,
                        session.hands[cpuIndex][drawnIndex],
                        session.activeColor
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
                        val calledUno = session.pendingUnoCall?.offenderIndex == cpuIndex
                        if (calledUno) {
                            session = GameEngine.announceUno(session, cpuIndex) ?: session
                        }
                        status = if (calledUno) {
                            "CPU ${cpuIndex} drew, played, and called UNO."
                        } else {
                            "CPU ${cpuIndex} drew and played ${cardName(drawnCard)}."
                        }
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
            GameEngine.isPlayable(session.topCard, card, session.activeColor)
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
        val feedbackEvent = pendingFeedback
        if (feedbackEvent != null) feedbackSequence += 1
        _uiState.value = GameUiState(
            matchId = matchId,
            matchDurationMillis = if (session.isOver) {
                (System.currentTimeMillis() - matchStartedAtMillis).coerceAtLeast(0L)
            } else {
                0L
            },
            feedbackSequence = feedbackSequence,
            feedbackEvent = feedbackEvent,
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
            chooseWildColor = chooseWildColor,
            wildDrawFourChallengeAvailable =
                session.pendingWildDrawFourChallenge?.targetIndex == HUMAN_PLAYER,
            pendingUnoCallDeadlineMillis = session.pendingUnoCall?.deadlineMillis,
            canAnnounceUno = session.pendingUnoCall?.offenderIndex == HUMAN_PLAYER
        )
        pendingFeedback = null
        if (statisticsRepository.isEnabled &&
            session.isOver &&
            savedStateHandle.get<String>(RECORDED_MATCH_KEY) != matchId &&
            recordingMatchId != matchId
        ) {
            val result = when {
                session.isDraw -> PracticeResult.DRAW
                session.winnerIndex == HUMAN_PLAYER -> PracticeResult.WIN
                else -> PracticeResult.LOSS
            }
            recordingMatchId = matchId
            viewModelScope.launch {
                try {
                    statisticsRepository.recordMatch(
                        PracticeMatchRecord(
                            matchId = matchId,
                            result = result,
                            difficulty = difficulty,
                            durationMillis = (System.currentTimeMillis() - matchStartedAtMillis)
                                .coerceAtLeast(0L),
                            finalHandScore = GameEngine.calculateScore(hand),
                            completedAtMillis = System.currentTimeMillis()
                        )
                    )
                    savedStateHandle[RECORDED_MATCH_KEY] = matchId
                } catch (error: IOException) {
                    recordingMatchId = null
                    _uiState.value = _uiState.value.copy(
                        statisticsSaveError = "Practice result could not be saved on this device."
                    )
                }
            }
        }
    }

    private fun effectMessage(card: GameCard, wildColor: CardColor?): String = when (card.value) {
        GameCard.SKIP -> "Skip played. The next player loses a turn."
        GameCard.REVERSE -> "Direction reversed."
        GameCard.DRAW_TWO -> "Draw Two played. The next player draws two and misses a turn."
        GameCard.WILD -> "Wild played. Active color: ${wildColor?.displayName()}."
        GameCard.WILD_DRAW_FOUR ->
            "Wild Draw Four played. The next player may challenge or draw four."
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
        const val MATCH_ID_KEY = "offline_game_match_id"
        const val MATCH_STARTED_AT_KEY = "offline_game_match_started_at"
        const val RECORDED_MATCH_KEY = "offline_game_recorded_match"
    }
}

class GameViewModelFactory(
    private val statisticsRepository: PracticeStatisticsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        require(modelClass == GameViewModel::class.java) {
            "GameViewModelFactory only creates GameViewModel."
        }
        return GameViewModel(extras.createSavedStateHandle(), statisticsRepository) as T
    }
}
