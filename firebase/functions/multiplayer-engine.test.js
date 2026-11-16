const test = require("node:test");
const assert = require("node:assert/strict");
const {
  CARD_VALUES,
  MultiplayerError,
  TURN_DURATION_MILLIS,
  applyAction,
  createGame,
  expireTurn,
  publicMatch,
} = require("./multiplayer-engine");

function players(count = 2) {
  return Array.from({ length: count }, (_, seat) => ({
    uid: `player-${seat}`,
    displayName: `Player ${seat}`,
    username: `player_${seat}`,
    gamerId: `AR-00000${seat}`,
    avatarId: "ember",
  }));
}

function card(color, value, isWild = false, id = `${color}-${value}`) {
  return { id, color, value, isWild };
}

function stateWith({ count = 2, hands, top = card("RED", 5), now = 100_000 } = {}) {
  const state = createGame(players(count), { nowMillis: now, matchId: "match-test" });
  state.hands = hands || Object.fromEntries(state.playerUids.map((uid) => [uid, []]));
  state.discardPile = [top];
  state.publicDiscardHistory = [top];
  state.drawPile = [card("BLUE", 9, false, "draw-1"), card("GREEN", 7, false, "draw-2")];
  state.activeColor = top.color;
  state.currentPlayerIndex = 0;
  state.turnNumber = 1;
  state.stateVersion = 1;
  state.hasDrawnThisTurn = false;
  state.drawnCardId = null;
  state.pendingChallenge = null;
  state.turnDeadlineMillis = now + TURN_DURATION_MILLIS;
  for (const player of state.players) player.cardCount = state.hands[player.uid].length;
  return state;
}

test("server creates a shuffled standard deck, deals seven private cards and exposes only public state", () => {
  const match = createGame(players(4), { nowMillis: 1_000, matchId: "private-match" });
  const cards = [
    ...Object.values(match.hands).flat(),
    ...match.drawPile,
    ...match.discardPile,
  ];
  assert.equal(cards.length, 108);
  assert.equal(new Set(cards.map((item) => item.id)).size, 108);
  assert.equal(match.players.every((player) => player.cardCount === 7), true);
  assert.equal(match.discardPile[0].value < CARD_VALUES.SKIP, true);
  const view = publicMatch(match);
  assert.equal("hands" in view, false);
  assert.equal("drawPile" in view, false);
  assert.equal("pendingChallenge" in view, false);
  assert.equal(view.players.length, 4);
});

test("only the current participant can play a card they own that matches the active color", () => {
  const match = stateWith({
    hands: {
      "player-0": [card("RED", 8, false, "owned")],
      "player-1": [card("BLUE", 8, false, "opponent")],
    },
  });
  assert.throws(
    () => applyAction(match, "outsider", { type: "DRAW_CARD" }, 101_000),
    (error) => error.code === "permission-denied"
  );
  assert.throws(
    () => applyAction(match, "player-1", { type: "DRAW_CARD" }, 101_000),
    (error) => error.code === "failed-precondition"
  );
  assert.throws(
    () => applyAction(match, "player-0", {
      type: "PLAY_CARD",
      cardId: "opponent",
      chosenColor: null,
    }, 101_000),
    (error) => error.code === "permission-denied"
  );
  assert.throws(
    () => applyAction(match, "player-0", {
      type: "PLAY_CARD",
      cardId: "owned",
      chosenColor: null,
    }, match.turnDeadlineMillis + 1),
    (error) => error.code === "deadline-exceeded"
  );
  const result = applyAction(match, "player-0", {
    type: "PLAY_CARD",
    cardId: "owned",
    chosenColor: null,
  }, 101_000);
  assert.equal(result.currentPlayerIndex, 1);
  assert.equal(result.players[0].cardCount, 0);
  assert.equal(result.winnerUid, "player-0");
  assert.equal(result.result.verified, true);
});

test("UNO calls and two-card catches use a server-enforced ten-second window", () => {
  const match = stateWith({
    hands: {
      "player-0": [card("RED", 2, false, "last-play"), card("BLUE", 4, false, "one-left")],
      "player-1": [card("GREEN", 7, false, "opponent")],
    },
  });
  const played = applyAction(match, "player-0", {
    type: "PLAY_CARD",
    cardId: "last-play",
  }, 101_000);

  assert.equal(played.pendingUno.offenderUid, "player-0");
  assert.equal(played.pendingUno.deadlineMillis, 111_000);
  assert.equal(publicMatch(played, 101_000).pendingUnoPlayerUid, "player-0");
  assert.equal(publicMatch(played, 111_001).pendingUnoPlayerUid, null);
  assert.throws(
    () => applyAction(played, "player-1", { type: "CALL_UNO" }, 102_000),
    (error) => error.code === "failed-precondition"
  );
  assert.throws(
    () => applyAction(played, "player-0", { type: "CATCH_UNO" }, 102_000),
    (error) => error.code === "failed-precondition"
  );

  const announced = applyAction(played, "player-0", { type: "CALL_UNO" }, 102_000);
  assert.equal(announced.pendingUno, null);
  assert.throws(
    () => applyAction(announced, "player-1", { type: "CATCH_UNO" }, 102_001),
    (error) => error.code === "failed-precondition"
  );

  const caught = applyAction(played, "player-1", { type: "CATCH_UNO" }, 103_000);
  assert.equal(caught.hands["player-0"].length, 3);
  assert.equal(caught.pendingUno, null);
  assert.throws(
    () => applyAction(played, "player-1", { type: "CATCH_UNO" }, 111_001),
    (error) => error.code === "failed-precondition"
  );

  const nextPlayerAction = applyAction(played, "player-1", { type: "DRAW_CARD" }, 104_000);
  assert.equal(nextPlayerAction.pendingUno, null);
});

test("wild selection, draw/pass, skip, reverse and draw-two use server-owned transitions", () => {
  const wild = stateWith({
    hands: {
      "player-0": [card("WILD", CARD_VALUES.WILD, true, "wild")],
      "player-1": [card("BLUE", 8, false, "blue")],
    },
  });
  assert.throws(
    () => applyAction(wild, "player-0", { type: "PLAY_CARD", cardId: "wild" }, 101_000),
    (error) => error.code === "invalid-argument"
  );
  const colored = applyAction(wild, "player-0", {
    type: "PLAY_CARD",
    cardId: "wild",
    chosenColor: "GREEN",
  }, 101_000);
  assert.equal(colored.activeColor, "GREEN");

  const draw = applyAction(wild, "player-0", { type: "DRAW_CARD" }, 101_000);
  assert.equal(draw.hands["player-0"].length, 2);
  assert.throws(
    () => applyAction(draw, "player-0", { type: "DRAW_CARD" }, 102_000),
    (error) => error.code === "failed-precondition"
  );
  const passed = applyAction(draw, "player-0", { type: "PASS_TURN" }, 102_000);
  assert.equal(passed.currentPlayerIndex, 1);

  const skip = stateWith({
    count: 3,
    hands: {
      "player-0": [card("RED", CARD_VALUES.SKIP, false, "skip")],
      "player-1": [card("BLUE", 1, false, "b")],
      "player-2": [card("GREEN", 2, false, "c")],
    },
  });
  assert.equal(applyAction(skip, "player-0", {
    type: "PLAY_CARD",
    cardId: "skip",
  }, 101_000).currentPlayerIndex, 2);

  const reverse = stateWith({
    count: 4,
    hands: {
      "player-0": [card("RED", CARD_VALUES.REVERSE, false, "reverse")],
      "player-1": [card("BLUE", 1, false, "b")],
      "player-2": [card("GREEN", 2, false, "c")],
      "player-3": [card("YELLOW", 3, false, "d")],
    },
  });
  const reversed = applyAction(reverse, "player-0", {
    type: "PLAY_CARD",
    cardId: "reverse",
  }, 101_000);
  assert.equal(reversed.direction, -1);
  assert.equal(reversed.currentPlayerIndex, 3);

  const drawTwo = stateWith({
    hands: {
      "player-0": [card("RED", CARD_VALUES.DRAW_TWO, false, "draw-two")],
      "player-1": [card("BLUE", 1, false, "b")],
    },
  });
  const penalized = applyAction(drawTwo, "player-0", {
    type: "PLAY_CARD",
    cardId: "draw-two",
  }, 101_000);
  assert.equal(penalized.hands["player-1"].length, 3);
  assert.equal(penalized.currentPlayerIndex, 0);
});

test("Wild Draw Four challenge preserves hidden legality and applies the correct penalties", () => {
  const legal = stateWith({
    hands: {
      "player-0": [card("WILD", CARD_VALUES.WILD_DRAW_FOUR, true, "wd4")],
      "player-1": [card("GREEN", 1, false, "target")],
    },
  });
  legal.drawPile = Array.from({ length: 10 }, (_, index) =>
    card("BLUE", index, false, `legal-draw-${index}`)
  );
  const pending = applyAction(legal, "player-0", {
    type: "PLAY_CARD",
    cardId: "wd4",
    chosenColor: "YELLOW",
  }, 101_000);
  assert.equal(publicMatch(pending).challengePending, true);
  assert.equal("pendingChallenge" in publicMatch(pending), false);
  const failedChallenge = applyAction(pending, "player-1", { type: "CHALLENGE" }, 102_000);
  assert.equal(failedChallenge.hands["player-1"].length, 7);
  assert.equal(failedChallenge.status, "COMPLETED");
  assert.equal(failedChallenge.winnerUid, "player-0");

  const illegal = stateWith({
    hands: {
      "player-0": [
        card("WILD", CARD_VALUES.WILD_DRAW_FOUR, true, "wd4-illegal"),
        card("RED", 2, false, "active-color"),
      ],
      "player-1": [card("GREEN", 1, false, "target")],
    },
  });
  illegal.drawPile = Array.from({ length: 10 }, (_, index) =>
    card("BLUE", index, false, `illegal-draw-${index}`)
  );
  const illegalPending = applyAction(illegal, "player-0", {
    type: "PLAY_CARD",
    cardId: "wd4-illegal",
    chosenColor: "BLUE",
  }, 101_000);
  const successfulChallenge = applyAction(illegalPending, "player-1", { type: "CHALLENGE" }, 102_000);
  assert.equal(successfulChallenge.hands["player-0"].length, 5);
  assert.equal(successfulChallenge.currentPlayerIndex, 1);
});

test("stale versions are left for transactional service validation and timeout expires only on server time", () => {
  const match = stateWith({
    hands: {
      "player-0": [card("RED", 1, false, "one")],
      "player-1": [card("BLUE", 2, false, "two")],
    },
  });
  assert.equal(expireTurn(match, match.turnDeadlineMillis - 1), null);
  const expired = expireTurn(match, match.turnDeadlineMillis);
  assert.equal(expired.players[0].timeoutCount, 1);
  assert.equal(expired.currentPlayerIndex, 1);
  assert.equal(expired.turnDeadlineMillis, match.turnDeadlineMillis + TURN_DURATION_MILLIS);
  assert.throws(
    () => applyAction(match, "player-0", { type: "PASS_TURN" }, match.turnDeadlineMillis + 1),
    (error) => error instanceof MultiplayerError && error.code === "deadline-exceeded"
  );
});
