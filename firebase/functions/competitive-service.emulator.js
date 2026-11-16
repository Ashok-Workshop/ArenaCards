const test = require("node:test");
const assert = require("node:assert/strict");
if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080") {
  throw new Error("Competitive service tests require the local Firestore Emulator at 127.0.0.1:8080.");
}
const { createFirestoreClient, FieldPath, FieldValue } = require("./firestore-client");
const { createCompetitiveService } = require("./competitive-service");
const { createMultiplayerService } = require("./multiplayer-service");

const db = createFirestoreClient();
let nowMillis = 1_000;

function request(uid, data = {}, claims = {}) {
  return {
    auth: {
      uid,
      token: {
        firebase: { sign_in_provider: "google.com" },
        ...claims,
      },
    },
    data,
  };
}

async function seedPlayer(uid, rating = 1_000) {
  await Promise.all([
    db.collection("users").doc(uid).set({
      uid,
      username: uid,
      displayName: `Display ${uid}`,
      gamerId: `AR-${String(uid.length * 100_000 + 100_000).slice(0, 6)}`,
      avatarId: "ember",
      accountStatus: "active",
      profileVisibility: "public",
      experiencePoints: 0,
      playerLevel: 1,
    }),
    db.collection("publicPlayers").doc(uid).set({
      gamerId: `AR-${String(uid.length * 100_000 + 100_000).slice(0, 6)}`,
      username: uid,
      displayName: `Display ${uid}`,
      avatarId: "ember",
      profileVisibility: "public",
    }),
    db.collection("competitiveProfiles").doc(uid).set({
      uid,
      rating,
      verifiedMatches: 0,
      wins: 0,
      losses: 0,
      winPercentage: 0,
      winRateEligible: false,
      bestRating: rating,
      bestRank: null,
      winStreak: 0,
      bestWinStreak: 0,
      isPublic: true,
    }),
    db.collection("onlinePlayers").doc(uid).set({ status: "IDLE", updatedAt: FieldValue.serverTimestamp() }),
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

test("competitive ratings, leaderboards, seasons and single-elimination tournaments are server-owned", async () => {
  const uids = ["comp-alpha", "comp-bravo", "comp-charlie"];
  const service = createCompetitiveService(db, { now: () => nowMillis });
  const multiplayer = createMultiplayerService(db, {
    now: () => nowMillis,
    prepareCompetitiveCompletion: service.prepareMatchCompletion,
    writeCompetitiveCompletion: service.writeMatchCompletion,
  });
  let matchId;
  try {
    for (const uid of uids) await seedPlayer(uid);
    await Promise.all([
      db.collection("competitiveProfiles").doc("comp-hidden").set({
        uid: "comp-hidden",
        rating: 1_008,
        verifiedMatches: 0,
        wins: 0,
        losses: 0,
        winPercentage: 0,
        winRateEligible: false,
        bestRating: 1_008,
        bestRank: null,
        winStreak: 0,
        bestWinStreak: 0,
        isPublic: true,
      }),
      db.collection("publicPlayers").doc("comp-hidden").set({
        profileVisibility: "private",
      }),
    ]);
    await assert.rejects(
      service.createSeason(request(uids[0], {
        seasonId: "season-auth-check",
        title: "Not admin",
        startsAtMillis: nowMillis + 1,
        endsAtMillis: nowMillis + 10,
      })),
      { code: "permission-denied" }
    );

    await service.createSeason(request("owner", {
      seasonId: "season-emulator",
      title: "Emulator Season",
      startsAtMillis: nowMillis,
      endsAtMillis: nowMillis + 10_000,
    }, { admin: true }));
    nowMillis += 1;
    await service.activateSeason(request("owner", { seasonId: "season-emulator" }, { admin: true }));

    const tournamentInput = {
      tournamentId: "cup-emulator",
      title: "Emulator Cup",
      description: "Local emulator verification",
      format: "SINGLE_ELIMINATION",
      maxParticipants: 2,
      registrationDeadlineMillis: nowMillis + 100,
      startsAtMillis: nowMillis + 200,
      rewards: { champion: { coins: 50, xp: 100 } },
    };
    await service.createTournament(request("owner", tournamentInput, { admin: true }));
    await service.registerTournament(request(uids[0], { tournamentId: "cup-emulator" }));
    await service.registerTournament(request(uids[1], { tournamentId: "cup-emulator" }));
    await assert.rejects(
      service.registerTournament(request(uids[2], { tournamentId: "cup-emulator" })),
      { code: "resource-exhausted" }
    );
    await assert.rejects(
      service.registerTournament(request(uids[0], { tournamentId: "cup-emulator" })),
      { code: "already-exists" }
    );
    await service.cancelTournamentRegistration(request(uids[0], { tournamentId: "cup-emulator" }));
    await service.registerTournament(request(uids[2], { tournamentId: "cup-emulator" }));

    nowMillis += 250;
    await service.startTournament(request("owner", { tournamentId: "cup-emulator" }, { admin: true }));
    const tournament = await service.getTournament(
      request(uids[1], { tournamentId: "cup-emulator" })
    );
    const activePairing = tournament.bracket.rounds.flat().find((match) => match.canStart);
    assert.ok(activePairing);
    const matchStart = await multiplayer.startTournamentMatch(request(uids[1], {
      tournamentId: "cup-emulator",
      tournamentMatchId: activePairing.matchId,
      requestId: "start-cup-match-0001",
    }));
    matchId = matchStart.matchId;
    const secondPlayerStart = await multiplayer.startTournamentMatch(request(
      activePairing.players.map((player) => player?.uid).find((player) => player !== uids[1]),
      {
        tournamentId: "cup-emulator",
        tournamentMatchId: activePairing.matchId,
        requestId: "start-cup-match-0002",
      }
    ));
    assert.equal(secondPlayerStart.matchId, matchStart.matchId);

    const winnerUid = uids[1];
    const loserUid = activePairing.players.map((player) => player?.uid)
      .find((player) => player !== winnerUid);
    const state = {
      matchId: matchStart.matchId,
      status: "COMPLETED",
      mode: "TOURNAMENT",
      tournamentId: "cup-emulator",
      tournamentMatchId: activePairing.matchId,
      playerUids: [winnerUid, loserUid],
      players: [{ uid: winnerUid }, { uid: loserUid }],
      winnerUid,
      result: {
        verified: true,
        winnerUid,
        completedAtMillis: nowMillis,
        positions: [
          { uid: winnerUid, position: 1 },
          { uid: loserUid, position: 2 },
        ],
      },
    };
    const rewards = state.playerUids.map((uid) => ({
      uid,
      coinAward: 25,
      newBalance: 25,
      totalCoinsEarned: 25,
      newXp: 50,
      xpAward: 50,
      newLevel: 1,
    }));
    let update;
    await db.runTransaction(async (transaction) => {
      update = await service.prepareMatchCompletion(transaction, state, rewards);
      service.writeMatchCompletion(transaction, update);
    });
    assert.equal(rewards.find((plan) => plan.uid === winnerUid).coinAward, 75);
    assert.equal(rewards.find((plan) => plan.uid === winnerUid).xpAward, 250);
    assert.equal(rewards.find((plan) => plan.uid === winnerUid).newLevel, 1);
    assert.equal((await db.collection("competitiveProfiles").doc(winnerUid).get()).get("wins"), 1);
    assert.equal((await db.collection("competitiveMatchRecords").doc(matchStart.matchId).get()).exists, true);

    const leaderboard = await service.getCompetitiveLeaderboard(
      request(uids[0], { category: "rating", pageSize: 10 })
    );
    assert.equal(leaderboard.entries.length, 3);
    assert.equal(leaderboard.entries[0].uid, winnerUid);
    assert.equal(leaderboard.entries.some((entry) => entry.uid === "comp-hidden"), false);
    const rankedProfiles = await db.collection("competitiveProfiles")
      .where("isPublic", "==", true)
      .orderBy("rating", "desc")
      .orderBy("wins", "desc")
      .orderBy(FieldPath.documentId(), "asc")
      .get();
    const expectedRanks = new Map(rankedProfiles.docs.map((profile, index) => [
      profile.id,
      index + 1,
    ]));
    assert.deepEqual(
      leaderboard.entries.map((entry) => entry.rank),
      leaderboard.entries.map((entry) => expectedRanks.get(entry.uid))
    );
    assert.equal(leaderboard.currentPlayer.rank > 0, true);
    const duplicate = await db.runTransaction((transaction) =>
      service.prepareMatchCompletion(transaction, state, rewards)
    );
    assert.equal(duplicate, null);

    nowMillis += 10_001;
    const finalized = await service.finalizeDueSeasons();
    assert.equal(finalized.finalizedCount, 1);
    assert.equal((await db.collection("competitiveSeasons").doc("season-emulator").get())
      .get("status"), "COMPLETED");
    const winnerWallet = await db.collection("wallets").doc(winnerUid).get();
    assert.equal(winnerWallet.get("coinsBalance"), 200);
  } finally {
    await Promise.all([
      ...uids.map((uid) => deleteTree(db.collection("users").doc(uid))),
      ...uids.map((uid) => deleteTree(db.collection("publicPlayers").doc(uid))),
      ...uids.map((uid) => deleteTree(db.collection("competitiveProfiles").doc(uid))),
      ...uids.map((uid) => deleteTree(db.collection("onlinePlayers").doc(uid))),
      deleteTree(db.collection("competitiveProfiles").doc("comp-hidden")),
      deleteTree(db.collection("publicPlayers").doc("comp-hidden")),
      deleteTree(db.collection("competitiveSeasons").doc("season-emulator")),
      deleteTree(db.collection("competitiveSeasonState").doc("current")),
      deleteTree(db.collection("competitiveSeasonFinalizations").doc("season-emulator")),
      ...(matchId ? [
        deleteTree(db.collection("onlineMatches").doc(matchId)),
        deleteTree(db.collection("competitiveMatchRecords").doc(matchId)),
        ...uids.map((uid) => deleteTree(db.collection("multiplayerRequests").doc(uid))),
      ] : []),
      deleteTree(db.collection("seasonLeaderboardEntries").doc(`season-emulator_${uids[0]}`)),
      deleteTree(db.collection("seasonLeaderboardEntries").doc(`season-emulator_${uids[1]}`)),
      deleteTree(db.collection("seasonLeaderboardEntries").doc(`season-emulator_${uids[2]}`)),
      deleteTree(db.collection("tournaments").doc("cup-emulator")),
      deleteTree(db.collection("tournamentParticipants").doc(uids[0])),
      deleteTree(db.collection("tournamentParticipants").doc(uids[1])),
      deleteTree(db.collection("tournamentParticipants").doc(uids[2])),
      ...uids.map((uid) => deleteTree(db.collection("competitiveRatingHistory").doc(uid))),
      ...uids.map((uid) => deleteTree(db.collection("competitiveAchievements").doc(uid))),
      ...uids.map((uid) => deleteTree(db.collection("inAppNotifications").doc(uid))),
      ...uids.map((uid) => deleteTree(db.collection("wallets").doc(uid))),
      ...uids.map((uid) => deleteTree(db.collection("progression").doc(uid))),
    ]);
  }
});
