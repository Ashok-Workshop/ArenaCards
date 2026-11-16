const { randomInt, randomUUID } = require("node:crypto");

const COLORS = Object.freeze(["RED", "BLUE", "GREEN", "YELLOW"]);
const CARD_VALUES = Object.freeze({
  SKIP: 10,
  REVERSE: 11,
  DRAW_TWO: 12,
  WILD: 13,
  WILD_DRAW_FOUR: 14,
});
const TURN_DURATION_MILLIS = 45_000;
const SUPPORTED_TURN_DURATIONS = Object.freeze([30_000, 45_000, 60_000]);
const UNO_CALL_WINDOW_MILLIS = 10_000;
const UNO_MISSED_CALL_PENALTY = 2;
const MAX_TIMEOUTS = 3;

class MultiplayerError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "MultiplayerError";
    this.code = code;
  }
}

function fail(code, message) {
  throw new MultiplayerError(code, message);
}

function createDeck() {
  const cards = [];
  function add(color, value, isWild = false) {
    cards.push({ id: randomUUID(), color, value, isWild });
  }

  for (const color of COLORS) {
    add(color, 0);
    for (let value = 1; value <= 9; value += 1) {
      add(color, value);
      add(color, value);
    }
    for (const value of [CARD_VALUES.SKIP, CARD_VALUES.REVERSE, CARD_VALUES.DRAW_TWO]) {
      add(color, value);
      add(color, value);
    }
  }
  for (let index = 0; index < 4; index += 1) {
    add("WILD", CARD_VALUES.WILD, true);
    add("WILD", CARD_VALUES.WILD_DRAW_FOUR, true);
  }
  for (let index = cards.length - 1; index > 0; index -= 1) {
    const swap = randomInt(index + 1);
    [cards[index], cards[swap]] = [cards[swap], cards[index]];
  }
  return cards;
}

function createGame(players, options = {}) {
  if (!Array.isArray(players) || players.length < 2 || players.length > 4) {
    fail("invalid-argument", "An online match requires two to four players.");
  }
  if (new Set(players.map((player) => player.uid)).size !== players.length) {
    fail("invalid-argument", "A player cannot occupy more than one seat.");
  }

  const nowMillis = options.nowMillis ?? Date.now();
  const rules = {
    turnDurationMillis: options.turnDurationMillis ?? TURN_DURATION_MILLIS,
    allowWildDrawFourChallenge: options.allowWildDrawFourChallenge ?? true,
    drawStacking: false,
  };
  if (!SUPPORTED_TURN_DURATIONS.includes(rules.turnDurationMillis)
    || typeof rules.allowWildDrawFourChallenge !== "boolean") {
    fail("invalid-argument", "The selected online match rules are not supported.");
  }
  const deck = createDeck();
  const hands = Object.fromEntries(players.map((player) => [player.uid, []]));
  for (let cardIndex = 0; cardIndex < 7; cardIndex += 1) {
    for (const player of players) hands[player.uid].push(deck.pop());
  }
  const openingIndex = deck.findIndex((card) =>
    !card.isWild && card.value < CARD_VALUES.SKIP
  );
  const openingCard = deck.splice(openingIndex, 1)[0];
  const seatedPlayers = players.map((player, seat) => ({
    uid: player.uid,
    displayName: player.displayName,
    username: player.username,
    gamerId: player.gamerId,
    avatarId: player.avatarId,
    seat,
    cardCount: hands[player.uid].length,
    ready: true,
    timeoutCount: 0,
    connectionState: "CONNECTED",
    lastSeenAtMillis: nowMillis,
  }));
  return {
    matchId: options.matchId || randomUUID(),
    status: "ACTIVE",
    mode: options.mode || "MATCHMAKING",
    ...(options.tournamentId ? { tournamentId: options.tournamentId } : {}),
    ...(options.tournamentMatchId ? { tournamentMatchId: options.tournamentMatchId } : {}),
    rules,
    playerUids: seatedPlayers.map((player) => player.uid),
    players: seatedPlayers,
    hands,
    drawPile: deck,
    discardPile: [openingCard],
    publicDiscardHistory: [openingCard],
    currentPlayerIndex: 0,
    direction: 1,
    activeColor: openingCard.color,
    turnNumber: 1,
    stateVersion: 1,
    hasDrawnThisTurn: false,
    drawnCardId: null,
    pendingChallenge: null,
    pendingUno: null,
    consecutiveUnplayedTurns: 0,
    createdAtMillis: nowMillis,
    updatedAtMillis: nowMillis,
    turnDeadlineMillis: nowMillis + rules.turnDurationMillis,
    winnerUid: null,
    result: null,
    completionReason: null,
  };
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function currentUid(state) {
  return state.playerUids[state.currentPlayerIndex];
}

function cardValueName(value) {
  return value >= CARD_VALUES.SKIP ? value : String(value);
}

function isPlayable(state, card) {
  const top = state.discardPile[state.discardPile.length - 1];
  return card.isWild
    || card.color === state.activeColor
    || card.value === top.value
    || card.value === CARD_VALUES.WILD_DRAW_FOUR;
}

function nextIndex(state, fromIndex = state.currentPlayerIndex, direction = state.direction) {
  return (fromIndex + direction + state.playerUids.length) % state.playerUids.length;
}

function drawCards(state, uid, count) {
  for (let index = 0; index < count; index += 1) {
    if (state.drawPile.length === 0 && state.discardPile.length > 1) {
      const top = state.discardPile.pop();
      const refill = state.discardPile;
      state.discardPile = [top];
      for (let cursor = refill.length - 1; cursor > 0; cursor -= 1) {
        const swap = randomInt(cursor + 1);
        [refill[cursor], refill[swap]] = [refill[swap], refill[cursor]];
      }
      state.drawPile = refill;
    }
    const card = state.drawPile.pop();
    if (card) state.hands[uid].push(card);
  }
}

function beginTurn(state, index, nowMillis) {
  state.currentPlayerIndex = index;
  state.turnNumber += 1;
  state.hasDrawnThisTurn = false;
  state.drawnCardId = null;
  state.turnDeadlineMillis = nowMillis + (state.rules?.turnDurationMillis || TURN_DURATION_MILLIS);
}

function completeMatch(state, winnerUid, nowMillis, reason = "CARDS_CLEARED") {
  state.status = "COMPLETED";
  state.winnerUid = winnerUid;
  state.completionReason = reason;
  state.turnDeadlineMillis = null;
  const order = [...state.players].sort((a, b) => {
    if (a.uid === winnerUid) return -1;
    if (b.uid === winnerUid) return 1;
    return state.hands[a.uid].length - state.hands[b.uid].length || a.seat - b.seat;
  });
  state.result = {
    verified: true,
    winnerUid,
    completedAtMillis: nowMillis,
    durationMillis: Math.max(0, nowMillis - state.createdAtMillis),
    completionReason: reason,
    positions: order.map((player, index) => ({
      uid: player.uid,
      position: index + 1,
      remainingCards: state.hands[player.uid].length,
    })),
  };
}

function updateCounts(state) {
  for (const player of state.players) player.cardCount = state.hands[player.uid].length;
}

function applyAction(source, uid, action, nowMillis = Date.now()) {
  if (source.status !== "ACTIVE") fail("failed-precondition", "This match is not active.");
  if (!source.playerUids.includes(uid)) fail("permission-denied", "You are not a participant in this match.");

  const state = clone(source);
  if (action.type === "CALL_UNO") {
    if (!state.pendingUno || state.pendingUno.offenderUid !== uid
      || nowMillis > state.pendingUno.deadlineMillis
      || state.hands[uid]?.length !== 1) {
      fail("failed-precondition", "There is no active UNO call for this player.");
    }
    state.pendingUno = null;
    state.stateVersion += 1;
    state.updatedAtMillis = nowMillis;
    updateCounts(state);
    return state;
  }
  if (action.type === "CATCH_UNO") {
    const pending = state.pendingUno;
    if (!pending || pending.offenderUid === uid || nowMillis > pending.deadlineMillis
      || state.hands[pending.offenderUid]?.length !== 1) {
      fail("failed-precondition", "There is no missed UNO call to catch.");
    }
    drawCards(state, pending.offenderUid, UNO_MISSED_CALL_PENALTY);
    state.pendingUno = null;
    state.stateVersion += 1;
    state.updatedAtMillis = nowMillis;
    updateCounts(state);
    return state;
  }
  if (nowMillis > source.turnDeadlineMillis) fail("deadline-exceeded", "This turn has expired.");
  if (currentUid(source) !== uid) fail("failed-precondition", "It is not your turn.");

  state.pendingUno = null;
  const playerIndex = state.currentPlayerIndex;
  const hand = state.hands[uid];

  if (state.pendingChallenge) {
    if (action.type !== "CHALLENGE" && action.type !== "ACCEPT_PENALTY") {
      fail("failed-precondition", "Respond to the Wild Draw Four before taking another action.");
    }
    const pending = state.pendingChallenge;
    const challenged = action.type === "CHALLENGE";
    const challengeSucceeded = challenged && pending.offenderHadActiveColor;
    if (challengeSucceeded) {
      drawCards(state, pending.offenderUid, 4);
      state.pendingChallenge = null;
      beginTurn(state, playerIndex, nowMillis);
    } else {
      drawCards(state, uid, challenged ? 6 : 4);
      state.pendingChallenge = null;
      const offenderHand = state.hands[pending.offenderUid];
      if (offenderHand.length === 0) {
        completeMatch(state, pending.offenderUid, nowMillis);
      } else {
        beginTurn(state, nextIndex(state), nowMillis);
      }
    }
  } else if (action.type === "PLAY_CARD") {
    if (typeof action.cardId !== "string") fail("invalid-argument", "A card ID is required.");
    const cardIndex = hand.findIndex((card) => card.id === action.cardId);
    if (cardIndex < 0) fail("permission-denied", "That card is not in your hand.");
    if (state.hasDrawnThisTurn && state.drawnCardId !== action.cardId) {
      fail("failed-precondition", "Only the card drawn this turn may be played.");
    }
    const card = hand[cardIndex];
    if (!isPlayable(state, card)) fail("failed-precondition", "That card is not playable.");
    if (card.isWild && !COLORS.includes(action.chosenColor)) {
      fail("invalid-argument", "Choose a valid color for a wild card.");
    }

    const hadActiveColor = hand.some((candidate) =>
      !candidate.isWild && candidate.color === state.activeColor
    );
    hand.splice(cardIndex, 1);
    if (hand.length === 1) {
      state.pendingUno = {
        offenderUid: uid,
        deadlineMillis: nowMillis + UNO_CALL_WINDOW_MILLIS,
      };
    }
    state.discardPile.push(card);
    state.publicDiscardHistory.push(card);
    state.activeColor = card.isWild ? action.chosenColor : card.color;
    state.pendingChallenge = null;

    const next = nextIndex(state);
    if (card.value === CARD_VALUES.WILD_DRAW_FOUR) {
      if (state.rules?.allowWildDrawFourChallenge !== false) {
        state.pendingChallenge = {
          offenderUid: uid,
          targetUid: state.playerUids[next],
          offenderHadActiveColor: hadActiveColor,
        };
        beginTurn(state, next, nowMillis);
        state.pendingChallenge = {
          offenderUid: uid,
          targetUid: state.playerUids[next],
          offenderHadActiveColor: hadActiveColor,
        };
      } else {
        drawCards(state, state.playerUids[next], 4);
        beginTurn(state, nextIndex(state, next), nowMillis);
      }
    } else if (card.value === CARD_VALUES.DRAW_TWO) {
      const targetUid = state.playerUids[next];
      drawCards(state, targetUid, 2);
      beginTurn(state, nextIndex(state, next), nowMillis);
    } else if (card.value === CARD_VALUES.SKIP) {
      beginTurn(state, nextIndex(state, next), nowMillis);
    } else if (card.value === CARD_VALUES.REVERSE) {
      state.direction *= -1;
      const following = state.playerUids.length === 2 ? playerIndex : nextIndex(state, playerIndex, state.direction);
      beginTurn(state, following, nowMillis);
    } else {
      beginTurn(state, next, nowMillis);
    }

    if (hand.length === 0 && !state.pendingChallenge) completeMatch(state, uid, nowMillis);
  } else if (action.type === "DRAW_CARD") {
    if (state.hasDrawnThisTurn) fail("failed-precondition", "A card has already been drawn this turn.");
    const before = hand.length;
    drawCards(state, uid, 1);
    state.hasDrawnThisTurn = true;
    state.drawnCardId = hand.length > before ? hand[hand.length - 1].id : null;
  } else if (action.type === "PASS_TURN") {
    if (!state.hasDrawnThisTurn) fail("failed-precondition", "Draw before ending your turn.");
    state.consecutiveUnplayedTurns += 1;
    const noCardsAvailable = state.drawPile.length === 0 && state.discardPile.length <= 1;
    if (noCardsAvailable && state.consecutiveUnplayedTurns >= state.playerUids.length) {
      completeMatch(state, null, nowMillis, "STALEMATE");
    } else {
      beginTurn(state, nextIndex(state), nowMillis);
    }
  } else {
    fail("invalid-argument", "That action is not supported.");
  }

  state.stateVersion += 1;
  state.updatedAtMillis = nowMillis;
  updateCounts(state);
  return state;
}

function expireTurn(source, nowMillis = Date.now()) {
  if (source.status !== "ACTIVE" || nowMillis < source.turnDeadlineMillis) return null;
  const state = clone(source);
  state.pendingUno = null;
  const uid = currentUid(state);
  const player = state.players.find((item) => item.uid === uid);
  player.timeoutCount += 1;
  if (state.pendingChallenge) {
    const pending = state.pendingChallenge;
    drawCards(state, pending.targetUid, 4);
    state.pendingChallenge = null;
    if (state.hands[pending.offenderUid].length === 0 && player.timeoutCount < MAX_TIMEOUTS) {
      completeMatch(state, pending.offenderUid, nowMillis);
    }
  }
  if (state.status === "ACTIVE" && player.timeoutCount >= MAX_TIMEOUTS) {
    state.status = "ABANDONED";
    state.completionReason = "TURN_TIMEOUT";
    state.turnDeadlineMillis = null;
    state.result = {
      verified: true,
      winnerUid: null,
      completedAtMillis: nowMillis,
      durationMillis: Math.max(0, nowMillis - state.createdAtMillis),
      completionReason: "TURN_TIMEOUT",
      positions: [],
    };
  } else if (state.status === "ACTIVE") {
    state.consecutiveUnplayedTurns += 1;
    beginTurn(state, nextIndex(state), nowMillis);
  }
  state.stateVersion += 1;
  state.updatedAtMillis = nowMillis;
  updateCounts(state);
  return state;
}

function publicMatch(state, nowMillis = Date.now()) {
  const pending = state.pendingChallenge;
  const pendingUno = state.pendingUno;
  return {
    matchId: state.matchId,
    status: state.status,
    mode: state.mode,
    playerUids: state.playerUids,
    players: state.players.map(({
      uid,
      displayName,
      username,
      gamerId,
      avatarId,
      seat,
      cardCount,
      timeoutCount,
      connectionState,
      lastSeenAtMillis,
    }) => ({
      uid,
      displayName,
      username,
      gamerId,
      avatarId,
      seat,
      cardCount,
      timeoutCount,
      connectionState,
      lastSeenAtMillis,
    })),
    currentPlayerUid: state.status === "ACTIVE" ? currentUid(state) : null,
    turnNumber: state.turnNumber,
    stateVersion: state.stateVersion,
    direction: state.direction,
    activeColor: state.activeColor,
    topCard: state.discardPile[state.discardPile.length - 1],
    discardHistory: state.publicDiscardHistory,
    capacity: state.playerUids.length,
    rules: state.rules || {
      turnDurationMillis: TURN_DURATION_MILLIS,
      allowWildDrawFourChallenge: true,
      drawStacking: false,
    },
    drawPileCount: state.drawPile.length,
    hasDrawnThisTurn: state.hasDrawnThisTurn,
    challengePending: Boolean(pending),
    challengeTargetUid: pending?.targetUid || null,
    pendingUnoPlayerUid: pendingUno?.deadlineMillis >= nowMillis
      ? pendingUno.offenderUid
      : null,
    unoDeadlineMillis: pendingUno?.deadlineMillis >= nowMillis
      ? pendingUno.deadlineMillis
      : null,
    turnDeadlineMillis: state.turnDeadlineMillis,
    createdAtMillis: state.createdAtMillis,
    updatedAtMillis: state.updatedAtMillis,
    result: state.result,
    completionReason: state.completionReason,
  };
}

module.exports = {
  CARD_VALUES,
  COLORS,
  MAX_TIMEOUTS,
  MultiplayerError,
  TURN_DURATION_MILLIS,
  UNO_CALL_WINDOW_MILLIS,
  UNO_MISSED_CALL_PENALTY,
  SUPPORTED_TURN_DURATIONS,
  applyAction,
  createGame,
  expireTurn,
  publicMatch,
};
