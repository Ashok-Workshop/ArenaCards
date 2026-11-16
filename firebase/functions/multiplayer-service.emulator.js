const test = require("node:test");
const assert = require("node:assert/strict");
if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080") {
  throw new Error("Multiplayer tests require the local Firestore Emulator at 127.0.0.1:8080.");
}
const { createFirestoreClient } = require("./firestore-client");
const { createMultiplayerService } = require("./multiplayer-service");
const { createGame, publicMatch } = require("./multiplayer-engine");

const db = createFirestoreClient();
const DAY = 24 * 60 * 60 * 1000;

function request(uid, data = {}, token = {}) {
  return {
    auth: {
      uid,
      token: {
        firebase: { sign_in_provider: "google.com" },
        ...token,
      },
    },
    data,
  };
}

async function seedPlayer(uid) {
  const profile = {
    uid,
    username: uid,
    displayName: `Display ${uid}`,
    gamerId: `AR-${String(uid.length * 123 + 100000).padStart(6, "0").slice(-6)}`,
    avatarId: "ember",
    accountStatus: "active",
    profileVisibility: "public",
    totalMatches: 0,
    wins: 0,
    losses: 0,
    draws: 0,
    experiencePoints: 0,
    playerLevel: 1,
  };
  await Promise.all([
    db.collection("users").doc(uid).set(profile),
    db.collection("publicPlayers").doc(uid).set({
      gamerId: profile.gamerId,
      username: profile.username,
      usernameNormalized: profile.username,
      displayName: profile.displayName,
      avatarId: profile.avatarId,
      profileVisibility: "public",
      totalMatches: 0,
      wins: 0,
      losses: 0,
      draws: 0,
      experiencePoints: 0,
      playerLevel: 1,
    }),
  ]);
}

async function deleteTree(reference) {
  for (const collection of await reference.listCollections()) {
    const snapshot = await collection.get();
    for (const document of snapshot.docs) await deleteTree(document.ref);
  }
  await reference.delete().catch((error) => {
    if (error.code !== 5) throw error;
  });
}

async function cleanup(uids) {
  const [rooms, matches] = await Promise.all([
    db.collection("privateRooms").where("memberUids", "array-contains-any", uids).get(),
    db.collection("onlineMatches").where("playerUids", "array-contains-any", uids).get(),
  ]);
  await Promise.all([
    ...rooms.docs.flatMap((room) => [
      deleteTree(room.ref),
      deleteTree(db.collection("roomCodeLookups").doc(room.get("roomCode"))),
    ]),
    ...matches.docs.map((match) => deleteTree(match.ref)),
  ]);
  const collections = [
    "users",
    "publicPlayers",
    "wallets",
    "progression",
    "onlinePlayers",
    "matchmakingQueue",
    "privateRooms",
    "roomJoinRateLimits",
    "onlineMatchHistory",
    "userBlocks",
    "multiplayerRequests",
  ];
  await Promise.all(uids.flatMap((uid) => collections.map((name) =>
    deleteTree(db.collection(name).doc(uid))
  )));
}

test("private rooms authorize access, ready players, start server-dealt matches and isolate hands", {
  timeout: 60_000,
}, async () => {
  const uids = ["room-owner-mp", "room-guest-mp", "room-outsider-mp"];
  const service = createMultiplayerService(db, {
    now: () => Date.UTC(2026, 8, 1, 12),
    codeGenerator: (() => {
      let counter = 0;
      return () => `ABCDEFGH2${counter++ + 2}`;
    })(),
  });
  try {
    await Promise.all(uids.map(seedPlayer));
    const created = await service.createPrivateRoom(request(uids[0], {
      playerCount: 2,
      turnDurationMillis: 60_000,
      allowWildDrawFourChallenge: false,
      requestId: "room-create-request-1",
    }));
    const duplicateCreate = await service.createPrivateRoom(request(uids[0], {
      playerCount: 2,
      turnDurationMillis: 60_000,
      allowWildDrawFourChallenge: false,
      requestId: "room-create-request-1",
    }));
    assert.deepEqual(duplicateCreate, created);
    await assert.rejects(
      service.getPrivateRoom(request(uids[2], { roomId: created.roomId })),
      (error) => error.code === "not-found"
    );
    const joined = await service.joinPrivateRoom(request(uids[1], {
      roomCode: created.roomCode,
      requestId: "room-join-request-1",
    }));
    assert.equal(joined.joined, true);
    const createdRoom = await service.getPrivateRoom(request(uids[0], { roomId: created.roomId }));
    assert.deepEqual(createdRoom.rules, {
      turnDurationMillis: 60_000,
      allowWildDrawFourChallenge: false,
      drawStacking: false,
    });
    await assert.rejects(
      service.updatePrivateRoomRules(request(uids[1], {
        roomId: created.roomId,
        requestId: "room-rules-guest",
        turnDurationMillis: 30_000,
        allowWildDrawFourChallenge: true,
      })),
      (error) => error.code === "permission-denied"
    );
    await service.updatePrivateRoomRules(request(uids[0], {
      roomId: created.roomId,
      requestId: "room-rules-owner",
      turnDurationMillis: 30_000,
      allowWildDrawFourChallenge: true,
    }));
    await service.setPrivateRoomReady(request(uids[1], {
      roomId: created.roomId,
      ready: true,
      requestId: "room-ready-request-1",
    }));
    const room = await service.getPrivateRoom(request(uids[0], { roomId: created.roomId }));
    assert.equal(room.players.every((player) => player.ready), true);
    assert.deepEqual(room.rules, {
      turnDurationMillis: 30_000,
      allowWildDrawFourChallenge: true,
      drawStacking: false,
    });

    const started = await service.startPrivateRoom(request(uids[0], {
      roomId: created.roomId,
      requestId: "room-start-request-1",
    }));
    const match = await db.collection("onlineMatches").doc(started.matchId).get();
    const privateState = await db.collection("onlineMatches").doc(started.matchId)
      .collection("private").doc("state").get();
    assert.equal(match.get("status"), "ACTIVE");
    assert.equal(match.get("capacity"), 2);
    assert.equal(match.get("rules.turnDurationMillis"), 30_000);
    assert.equal(match.get("rules.allowWildDrawFourChallenge"), true);
    assert.equal(match.get("turnDeadlineMillis") - match.get("createdAtMillis"), 30_000);
    await assert.rejects(
      service.updatePrivateRoomRules(request(uids[0], {
        roomId: created.roomId,
        requestId: "room-rules-after-start",
        turnDurationMillis: 60_000,
        allowWildDrawFourChallenge: false,
      })),
      (error) => error.code === "failed-precondition"
    );
    assert.equal("hands" in match.data(), false);
    assert.equal(Object.keys(privateState.get("hands")).length, 2);
    assert.equal((await service.getPrivateRoom(request(uids[0], {
      roomId: created.roomId,
    }))).status, "MATCHED");

    const ownerResume = await service.reconnectToMatch(request(uids[0], {
      matchId: started.matchId,
    }));
    const guestResume = await service.reconnectToMatch(request(uids[1], {
      matchId: started.matchId,
    }));
    assert.equal(ownerResume.hand.length, 7);
    assert.equal(guestResume.hand.length, 7);
    assert.notDeepEqual(ownerResume.hand, guestResume.hand);
    await assert.rejects(
      service.reconnectToMatch(request(uids[2], { matchId: started.matchId })),
      (error) => error.code === "not-found"
    );
    assert.equal((await service.observeOnlinePlayer(request(uids[0]))).status, "IN_MATCH");
  } finally {
    await cleanup(uids);
  }
});

test("matchmaking pairs only compatible queue entries and prevents duplicate active assignments", {
  timeout: 60_000,
}, async () => {
  const uids = ["queue-player-a", "queue-player-b", "queue-player-c"];
  const service = createMultiplayerService(db);
  try {
    await Promise.all(uids.map(seedPlayer));
    const results = await Promise.all([
      service.joinMatchmaking(request(uids[0], {
        playerCount: 2,
        requestId: "queue-request-a-0001",
      })),
      service.joinMatchmaking(request(uids[1], {
        playerCount: 2,
        requestId: "queue-request-b-0001",
      })),
    ]);
    assert.equal(results.filter((item) => item.matched).length, 1);
    const matchId = results.find((item) => item.matched).matchId;
    for (const uid of uids.slice(0, 2)) {
      const state = await service.observeOnlinePlayer(request(uid));
      assert.equal(state.status, "IN_MATCH");
      assert.equal(state.matchId, matchId);
      const duplicate = await service.joinMatchmaking(request(uid, {
        playerCount: 2,
        requestId: `duplicate-${uid}`,
      }));
      assert.equal(duplicate.matchId, matchId);
    }

    const waiting = await service.joinMatchmaking(request(uids[2], {
      playerCount: 3,
      requestId: "queue-request-c-0001",
    }));
    assert.equal(waiting.matched, false);
    assert.equal((await service.cancelMatchmaking(request(uids[2]))).cancelled, true);
    assert.equal((await service.observeOnlinePlayer(request(uids[2]))).status, "IDLE");
  } finally {
    await cleanup(uids);
  }
});

test("private room-code guessing is rate limited without revealing room existence", {
  timeout: 60_000,
}, async () => {
  const uid = "room-guessing-player";
  const service = createMultiplayerService(db, { now: () => Date.UTC(2026, 8, 1, 12) });
  try {
    await seedPlayer(uid);
    for (let attempt = 0; attempt < 10; attempt += 1) {
      await assert.rejects(
        service.joinPrivateRoom(request(uid, {
          roomCode: "ZZZZZZZZZZ",
          requestId: `guess-request-${String(attempt).padStart(3, "0")}`,
        })),
        (error) => error.code === "not-found"
      );
    }
    await assert.rejects(
      service.joinPrivateRoom(request(uid, {
        roomCode: "ZZZZZZZZZZ",
        requestId: "guess-request-final",
      })),
      (error) => error.code === "resource-exhausted"
    );
  } finally {
    await cleanup([uid]);
  }
});

test("blocked players cannot join private rooms", { timeout: 60_000 }, async () => {
  const uids = ["blocked-room-owner", "blocked-room-guest"];
  const service = createMultiplayerService(db);
  try {
    await Promise.all(uids.map(seedPlayer));
    const room = await service.createPrivateRoom(request(uids[0], {
      playerCount: 2,
      requestId: "blocked-room-create",
    }));
    await db.collection("userBlocks").doc(uids[0]).collection("users").doc(uids[1])
      .set({ createdAt: new Date() });

    await assert.rejects(
      service.joinPrivateRoom(request(uids[1], {
        roomCode: room.roomCode,
        requestId: "blocked-room-join",
      })),
      (error) => error.code === "not-found"
    );
    assert.deepEqual(
      (await service.getPrivateRoom(request(uids[0], { roomId: room.roomId }))).memberUids,
      [uids[0]]
    );
    assert.equal((await service.observeOnlinePlayer(request(uids[1]))).status, "IDLE");
  } finally {
    await cleanup(uids);
  }
});

test("verified match completion atomically rewards once and creates private paginated history", {
  timeout: 60_000,
}, async () => {
  const uids = ["match-winner-player", "match-loser-player"];
  const service = createMultiplayerService(db);
  try {
    await Promise.all(uids.map(seedPlayer));
    const [queued, matched] = await Promise.all([
      service.joinMatchmaking(request(uids[0], {
        playerCount: 2,
        requestId: "match-queue-winner",
      })),
      service.joinMatchmaking(request(uids[1], {
        playerCount: 2,
        requestId: "match-queue-loser",
      })),
    ]);
    const matchId = queued.matchId || matched.matchId;
    assert.ok(matchId);
    const created = await db.collection("onlineMatches").doc(matchId).get();
    const stateRef = db.collection("onlineMatches").doc(matchId)
      .collection("private").doc("state");
    const stateSnapshot = await stateRef.get();
    const state = stateSnapshot.data();
    state.hands[uids[0]] = [{ id: "winner-card", color: "RED", value: 7, isWild: false }];
    state.hands[uids[1]] = [{ id: "loser-card", color: "BLUE", value: 2, isWild: false }];
    state.drawPile = [];
    state.discardPile = [{ id: "top-card", color: "RED", value: 5, isWild: false }];
    state.publicDiscardHistory = state.discardPile;
    state.activeColor = "RED";
    state.currentPlayerIndex = state.playerUids.indexOf(uids[0]);
    state.hasDrawnThisTurn = false;
    state.drawnCardId = null;
    state.pendingChallenge = null;
    state.turnDeadlineMillis = Date.now() + 45_000;
    state.stateVersion = created.get("stateVersion");
    state.turnNumber = created.get("turnNumber");
    for (const player of state.players) {
      player.cardCount = state.hands[player.uid].length;
      await db.collection("onlineMatches").doc(matchId).collection("hands")
        .doc(player.uid).set({ cards: state.hands[player.uid], stateVersion: state.stateVersion });
    }
    const { publicMatch } = require("./multiplayer-engine");
    await Promise.all([
      stateRef.set(state),
      db.collection("onlineMatches").doc(matchId).set(publicMatch(state), { merge: true }),
    ]);
    const expectedStateVersion = state.stateVersion;
    const expectedTurnNumber = state.turnNumber;
    const result = await service.submitMatchAction(request(uids[0], {
      matchId,
      action: "PLAY_CARD",
      cardId: "winner-card",
      expectedStateVersion,
      expectedTurnNumber,
      requestId: "verified-match-action-001",
    }));
    assert.equal(result.status, "COMPLETED");
    assert.equal(
      (await db.collection("onlineMatches").doc(matchId).get()).get("result.winnerUid"),
      uids[0]
    );
    assert.equal((await db.collection("wallets").doc(uids[0]).get()).get("coinsBalance"), 100);
    assert.equal((await db.collection("wallets").doc(uids[1]).get()).get("coinsBalance"), 25);
    assert.equal((await db.collection("users").doc(uids[0]).get()).get("wins"), 1);
    assert.equal((await db.collection("users").doc(uids[1]).get()).get("losses"), 1);
    assert.equal((await db.collection("wallets").doc(uids[0]).collection("transactions").get()).size, 1);

    const replay = await service.submitMatchAction(request(uids[0], {
      matchId,
      action: "PLAY_CARD",
      cardId: "winner-card",
      expectedStateVersion,
      expectedTurnNumber,
      requestId: "verified-match-action-001",
    }));
    assert.deepEqual(replay, {
      accepted: true,
      stateVersion: result.stateVersion,
    });
    assert.equal((await db.collection("wallets").doc(uids[0]).collection("transactions").get()).size, 1);

    const winnerHistory = await service.getMatchHistory(request(uids[0], { pageSize: 1 }));
    assert.equal(winnerHistory.matches.length, 1);
    assert.equal(winnerHistory.matches[0].verified, true);
    assert.equal(winnerHistory.matches[0].outcome, "WIN");
    assert.equal(winnerHistory.nextCursor, null);
    await assert.rejects(
      service.getMatchHistory(request(uids[1], {
        pageSize: 1,
        beforeCompletedAtMillis: Date.now(),
      })),
      (error) => error.code === "invalid-argument"
    );
    await assert.rejects(
      service.getMatchHistory(request(uids[0], { pageSize: 51 })),
      (error) => error.code === "invalid-argument"
    );
  } finally {
    await cleanup(uids);
  }
});

test("match transactions validate UNO calls and catches and publish pending-call state", {
  timeout: 60_000,
}, async () => {
  const uids = ["uno-call-offender", "uno-call-opponent"];
  let nowMillis = Date.UTC(2026, 8, 1, 12);
  const service = createMultiplayerService(db, { now: () => nowMillis });
  try {
    await Promise.all(uids.map(seedPlayer));
    const queued = await Promise.all(uids.map((uid, index) =>
      service.joinMatchmaking(request(uid, {
        playerCount: 2,
        requestId: `uno-queue-request-${index}`,
      }))
    ));
    const matchId = queued.find((result) => result.matchId)?.matchId;
    assert.ok(matchId);

    const matchRef = db.collection("onlineMatches").doc(matchId);
    const stateRef = matchRef.collection("private").doc("state");
    const matchSnapshot = await matchRef.get();
    const state = (await stateRef.get()).data();
    const offenderUid = state.playerUids[state.currentPlayerIndex];
    const opponentUid = state.playerUids.find((uid) => uid !== offenderUid);
    state.hands[offenderUid] = [
      { id: "uno-played-card", color: "RED", value: 2, isWild: false },
      { id: "uno-last-card", color: "BLUE", value: 4, isWild: false },
    ];
    state.hands[opponentUid] = [
      { id: "uno-opponent-card", color: "GREEN", value: 7, isWild: false },
    ];
    state.drawPile = [
      { id: "uno-draw-1", color: "YELLOW", value: 1, isWild: false },
      { id: "uno-draw-2", color: "BLUE", value: 3, isWild: false },
      { id: "uno-draw-3", color: "GREEN", value: 5, isWild: false },
      { id: "uno-draw-4", color: "YELLOW", value: 8, isWild: false },
    ];
    state.discardPile = [{ id: "uno-top-card", color: "RED", value: 5, isWild: false }];
    state.publicDiscardHistory = state.discardPile;
    state.activeColor = "RED";
    state.hasDrawnThisTurn = false;
    state.drawnCardId = null;
    state.pendingChallenge = null;
    state.pendingUno = null;
    state.stateVersion = matchSnapshot.get("stateVersion");
    state.turnNumber = matchSnapshot.get("turnNumber");
    state.turnDeadlineMillis = nowMillis + 45_000;
    for (const player of state.players) {
      player.cardCount = state.hands[player.uid].length;
      await matchRef.collection("hands").doc(player.uid).set({
        cards: state.hands[player.uid],
        stateVersion: state.stateVersion,
      });
    }
    await Promise.all([
      stateRef.set(state),
      matchRef.set(publicMatch(state, nowMillis), { merge: true }),
    ]);

    const played = await service.submitMatchAction(request(offenderUid, {
      matchId,
      action: "PLAY_CARD",
      cardId: "uno-played-card",
      expectedStateVersion: state.stateVersion,
      expectedTurnNumber: state.turnNumber,
      requestId: "uno-play-request-1",
    }));
    let updatedState = (await stateRef.get()).data();
    assert.equal(updatedState.pendingUno.offenderUid, offenderUid);
    assert.equal(updatedState.pendingUno.deadlineMillis, nowMillis + 10_000);
    assert.equal((await matchRef.get()).get("pendingUnoPlayerUid"), offenderUid);
    assert.equal((await matchRef.get()).get("unoDeadlineMillis"), nowMillis + 10_000);

    await assert.rejects(
      service.submitMatchAction(request(opponentUid, {
        matchId,
        action: "CALL_UNO",
        expectedStateVersion: played.stateVersion,
        expectedTurnNumber: updatedState.turnNumber,
        requestId: "uno-call-wrong-player",
      })),
      (error) => error.code === "failed-precondition"
    );
    nowMillis += 1_000;
    const called = await service.submitMatchAction(request(offenderUid, {
      matchId,
      action: "CALL_UNO",
      expectedStateVersion: played.stateVersion,
      expectedTurnNumber: updatedState.turnNumber,
      requestId: "uno-call-valid",
    }));
    updatedState = (await stateRef.get()).data();
    assert.equal(called.accepted, true);
    assert.equal(updatedState.pendingUno, null);
    assert.equal((await matchRef.get()).get("pendingUnoPlayerUid"), null);

    const catchState = JSON.parse(JSON.stringify(updatedState));
    delete catchState.updatedAt;
    catchState.stateVersion += 1;
    catchState.pendingUno = {
      offenderUid,
      deadlineMillis: nowMillis + 10_000,
    };
    for (const player of catchState.players) {
      player.cardCount = catchState.hands[player.uid].length;
      await matchRef.collection("hands").doc(player.uid).set({
        cards: catchState.hands[player.uid],
        stateVersion: catchState.stateVersion,
      });
    }
    await Promise.all([
      stateRef.set(catchState),
      matchRef.set(publicMatch(catchState, nowMillis), { merge: true }),
    ]);
    await assert.rejects(
      service.submitMatchAction(request(offenderUid, {
        matchId,
        action: "CATCH_UNO",
        expectedStateVersion: catchState.stateVersion,
        expectedTurnNumber: catchState.turnNumber,
        requestId: "uno-catch-self",
      })),
      (error) => error.code === "failed-precondition"
    );
    nowMillis += 1_000;
    const caught = await service.submitMatchAction(request(opponentUid, {
      matchId,
      action: "CATCH_UNO",
      expectedStateVersion: catchState.stateVersion,
      expectedTurnNumber: catchState.turnNumber,
      requestId: "uno-catch-valid",
    }));
    updatedState = (await stateRef.get()).data();
    assert.equal(caught.accepted, true);
    assert.equal(updatedState.hands[offenderUid].length, 3);
    assert.equal(updatedState.pendingUno, null);
    assert.equal((await matchRef.get()).get("pendingUnoPlayerUid"), null);
  } finally {
    await cleanup(uids);
  }
});

test("stale actions are rejected and timeout transitions require the server deadline", {
  timeout: 60_000,
}, async () => {
  const uids = ["timeout-player-a", "timeout-player-b"];
  let nowMillis = Date.UTC(2026, 8, 1, 12);
  const service = createMultiplayerService(db, { now: () => nowMillis });
  try {
    await Promise.all(uids.map(seedPlayer));
    const queueResults = await Promise.all(uids.map((uid, index) =>
      service.joinMatchmaking(request(uid, {
        playerCount: 2,
        requestId: `timeout-queue-${index}`,
      }))
    ));
    const matchId = queueResults.find((result) => result.matchId)?.matchId;
    assert.ok(matchId);
    const matchRef = db.collection("onlineMatches").doc(matchId);
    const initial = await matchRef.get();
    const initialVersion = initial.get("stateVersion");
    const currentUid = initial.get("currentPlayerUid");

    await assert.rejects(
      service.submitMatchAction(request(currentUid, {
        matchId,
        action: "DRAW_CARD",
        expectedTurnNumber: initial.get("turnNumber"),
        expectedStateVersion: initialVersion - 1,
        requestId: "stale-turn-action",
      })),
      (error) => error.code === "aborted"
    );
    const beforeDeadline = await service.processExpiredTurn(request(uids[0], {
      matchId,
      requestId: "timeout-before-deadline",
    }));
    assert.equal(beforeDeadline.expired, false);
    assert.equal((await matchRef.get()).get("stateVersion"), initialVersion);

    const state = await matchRef.collection("private").doc("state").get();
    nowMillis = state.get("turnDeadlineMillis") + 1;
    const expired = await service.processExpiredTurn(request(uids[1], {
      matchId,
      requestId: "timeout-after-deadline",
    }));
    assert.equal(expired.expired, true);
    assert.equal((await matchRef.get()).get("stateVersion"), initialVersion + 1);
    assert.notEqual((await matchRef.get()).get("currentPlayerUid"), currentUid);
    assert.equal(
      (await matchRef.collection("private").doc("state").get())
        .get("players").find((player) => player.uid === currentUid).timeoutCount,
      1
    );
  } finally {
    await cleanup(uids);
  }
});

test("scheduled timeout workers are idempotent, race-safe, and abandon repeated no-shows", {
  timeout: 60_000,
}, async () => {
  const uids = ["scheduled-timeout-a", "scheduled-timeout-b"];
  let nowMillis = Date.UTC(2026, 8, 1, 12);
  const service = createMultiplayerService(db, { now: () => nowMillis });
  try {
    await Promise.all(uids.map(seedPlayer));
    const matches = await Promise.all(uids.map((uid, index) =>
      service.joinMatchmaking(request(uid, {
        playerCount: 2,
        requestId: `scheduled-timeout-queue-${index}`,
      }))
    ));
    const matchId = matches.find((result) => result.matchId)?.matchId;
    assert.ok(matchId);
    const matchRef = db.collection("onlineMatches").doc(matchId);
    const initialState = await matchRef.collection("private").doc("state").get();

    nowMillis = initialState.get("turnDeadlineMillis") - 1;
    await service.reconnectToMatch(request(initialState.get("playerUids")[0], { matchId }));
    assert.equal((await service.processDueTimeouts()).processed, 0);
    assert.equal((await matchRef.get()).get("stateVersion"), initialState.get("stateVersion"));

    let processedTurns = 0;
    for (let turn = 0; turn < 6; turn += 1) {
      const currentState = await matchRef.collection("private").doc("state").get();
      nowMillis = currentState.get("turnDeadlineMillis") + 1;
      const workerResults = await Promise.all([
        service.processDueTimeouts({ workerRunId: `timeout-worker-a-${turn}` }),
        service.processDueTimeouts({ workerRunId: `timeout-worker-b-${turn}` }),
      ]);
      processedTurns += workerResults.reduce((sum, result) => sum + result.processed, 0);
      assert.equal(workerResults.reduce((sum, result) => sum + result.processed, 0), 1);
      const publicMatch = await matchRef.get();
      if (publicMatch.get("status") === "ABANDONED") break;
    }

    const finalMatch = await matchRef.get();
    assert.equal(finalMatch.get("status"), "ABANDONED");
    assert.equal(processedTurns, 5);
    assert.equal((await matchRef.collection("timeouts").get()).size, 5);
    assert.equal((await db.collection("wallets").doc(uids[0]).get()).exists, false);
    assert.equal((await db.collection("wallets").doc(uids[1]).get()).exists, false);
    assert.equal((await db.collection("onlinePlayers").doc(uids[0]).get()).get("status"), "IDLE");
    assert.equal((await service.processDueTimeouts()).scanned, 0);
  } finally {
    await cleanup(uids);
  }
});

test("stale versions, forged rewards and unverified password users are rejected", {
  timeout: 60_000,
}, async () => {
  const uid = "match-auth-check";
  const service = createMultiplayerService(db);
  try {
    await seedPlayer(uid);
    await assert.rejects(
      service.joinMatchmaking(request(uid, {
        playerCount: 2,
        requestId: "match-unverified-password",
      }, {
        firebase: { sign_in_provider: "password" },
        email_verified: false,
      })),
      (error) => error.code === "permission-denied"
    );
    await assert.rejects(
      service.joinMatchmaking(request(uid, {
        playerCount: 2,
        requestId: "match-forged-amount",
        amount: 900_000,
      })),
      (error) => error.code === "invalid-argument"
    );
    assert.equal((await db.collection("wallets").doc(uid).get()).exists, false);
  } finally {
    await cleanup([uid]);
  }
});

test.after(async () => {
  await db.terminate();
});
