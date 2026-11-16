const test = require("node:test");
const assert = require("node:assert/strict");
if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080") {
  throw new Error("Wallet tests require the local Firestore Emulator at 127.0.0.1:8080.");
}
const { createFirestoreClient } = require("./firestore-client");
const { createWalletService } = require("./wallet-service");

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

async function deleteDocumentTree(reference) {
  for (const collection of await reference.listCollections()) {
    const snapshot = await collection.get();
    for (const document of snapshot.docs) await deleteDocumentTree(document.ref);
  }
  await reference.delete().catch((error) => {
    if (error.code !== 5) throw error;
  });
}

async function cleanup(uid) {
  await Promise.all([
    "wallets",
    "dailyRewards",
    "dailyRewardClaims",
    "missions",
    "missionClaims",
    "progression",
    "walletRateLimits",
    "users",
    "publicPlayers",
  ].map((collection) => deleteDocumentTree(db.collection(collection).doc(uid))));
}

test("server wallet, UTC rewards, missions, progression and ledger are atomic", {
  timeout: 60_000,
}, async () => {
  const uid = `wallet-emulator-${Date.now()}`;
  let currentTime = Date.UTC(2026, 0, 1, 12);
  const service = createWalletService(db, {
    now: () => currentTime,
    claimRateLimitMillis: 0,
  });

  try {
    await db.collection("users").doc(uid).set({
      experiencePoints: 990,
      playerLevel: 1,
      accountStatus: "active",
    });
    await db.collection("publicPlayers").doc(uid).set({
      experiencePoints: 990,
      playerLevel: 1,
      profileVisibility: "private",
    });

    const initial = await service.getArenaAccount(request(uid));
    assert.equal(initial.wallet.coinsBalance, 0);
    assert.equal(initial.wallet.totalCoinsEarned, 0);
    assert.equal(initial.wallet.totalCoinsSpent, 0);
    assert.ok(Number.isSafeInteger(initial.wallet.createdAtMillis));
    assert.ok(Number.isSafeInteger(initial.wallet.updatedAtMillis));
    assert.equal(initial.dailyReward.resetTimezone, "UTC");
    assert.equal(initial.dailyReward.claimAvailable, true);
    assert.equal(initial.progression.experiencePoints, 990);
    assert.equal(initial.progression.level, 1);
    assert.equal(initial.missions.length, 2);

    await assert.rejects(
      service.getArenaAccount({ data: {} }),
      (error) => error.code === "unauthenticated"
    );
    await assert.rejects(
      service.claimDailyReward(request(uid, {
        requestId: "wallet-request-forged",
        amount: 900_000,
      })),
      (error) => error.code === "invalid-argument"
    );

    const firstClaim = await service.claimDailyReward(
      request(uid, { requestId: "wallet-daily-20260101" })
    );
    assert.equal(firstClaim.claimed, true);
    assert.equal(firstClaim.streakDays, 1);
    assert.equal(firstClaim.rewardCoins, 50);
    assert.equal(firstClaim.coinsBalance, 50);
    assert.equal(firstClaim.experiencePoints, 1_015);
    assert.equal(firstClaim.level, 2);

    const duplicateClaim = await service.claimDailyReward(
      request(uid, { requestId: "wallet-daily-retry-20260101" })
    );
    assert.equal(duplicateClaim.claimed, false);
    assert.equal(duplicateClaim.alreadyClaimed, true);
    assert.equal(duplicateClaim.coinsBalance, 50);

    const dailyLoginMission = await service.claimMissionReward(
      request(uid, {
        missionId: "daily_login",
        requestId: "wallet-mission-login-20260101",
      })
    );
    assert.equal(dailyLoginMission.claimed, true);
    assert.equal(dailyLoginMission.rewardAmount, 0);
    assert.equal(dailyLoginMission.coinsBalance, 50);
    assert.equal(dailyLoginMission.experiencePoints, 1_040);
    assert.equal(dailyLoginMission.level, 2);

    const duplicateMission = await service.claimMissionReward(
      request(uid, {
        missionId: "daily_login",
        requestId: "wallet-mission-retry-20260101",
      })
    );
    assert.equal(duplicateMission.alreadyClaimed, true);

    currentTime += DAY;
    const secondClaim = await service.claimDailyReward(
      request(uid, { requestId: "wallet-daily-20260102" })
    );
    assert.equal(secondClaim.streakDays, 2);
    assert.equal(secondClaim.rewardCoins, 75);

    currentTime += DAY;
    const thirdClaim = await service.claimDailyReward(
      request(uid, { requestId: "wallet-daily-20260103" })
    );
    assert.equal(thirdClaim.streakDays, 3);
    assert.equal(thirdClaim.rewardCoins, 100);
    const streakMission = await service.claimMissionReward(
      request(uid, {
        missionId: "three_day_streak",
        requestId: "wallet-mission-streak-20260103",
      })
    );
    assert.equal(streakMission.rewardAmount, 30);
    assert.equal(streakMission.coinsBalance, 255);

    const account = await service.getArenaAccount(request(uid));
    assert.equal(account.wallet.coinsBalance, 255);
    assert.equal(account.wallet.totalCoinsEarned, 255);
    assert.equal(account.wallet.totalCoinsSpent, 0);
    assert.equal(
      account.wallet.totalCoinsEarned - account.wallet.totalCoinsSpent,
      account.wallet.coinsBalance
    );
    assert.equal(account.progression.level, 2);
    assert.equal(account.achievements.find((item) => item.id === "daily_streak_3").unlocked, true);
    assert.equal(account.achievements.find((item) => item.id === "daily_streak_7").unlocked, false);
    assert.deepEqual(account.achievementUnlockHistory.map((item) => item.id), ["daily_streak_3"]);
    assert.equal(
      (await db.collection("progression").doc(uid)
        .collection("achievementHistory").doc("daily_streak_3").get()).exists,
      true
    );
    assert.equal(
      (await db.collection("users").doc(uid).get()).get("playerLevel"),
      2
    );
    assert.equal(
      (await db.collection("publicPlayers").doc(uid).get()).get("experiencePoints"),
      account.progression.experiencePoints
    );

    const transactionSnapshot = await db.collection("wallets").doc(uid)
      .collection("transactions").get();
    assert.equal(transactionSnapshot.size, 4);
    assert.equal(transactionSnapshot.docs.every((doc) =>
      doc.get("status") === "COMMITTED"
      && doc.get("balanceAfter") - doc.get("balanceBefore") === doc.get("amount")), true);

    const firstPage = await service.getWalletTransactions(
      request(uid, { pageSize: 2 })
    );
    assert.equal(firstPage.transactions.length, 2);
    assert.ok(firstPage.nextCursor);
    const secondPage = await service.getWalletTransactions(
      request(uid, { pageSize: 2, ...firstPage.nextCursor })
    );
    assert.equal(secondPage.transactions.length, 2);
    assert.equal(secondPage.nextCursor, null);
    assert.equal(
      new Set([...firstPage.transactions, ...secondPage.transactions]
        .map((item) => item.transactionId)).size,
      4
    );
    await assert.rejects(
      service.getWalletTransactions(request(uid, { pageSize: 100 })),
      (error) => error.code === "invalid-argument"
    );
  } finally {
    await cleanup(uid);
  }
});

test("concurrent daily claims award once and a missed UTC day resets the streak", {
  timeout: 60_000,
}, async () => {
  const uid = `wallet-concurrency-${Date.now()}`;
  let currentTime = Date.UTC(2026, 4, 1, 12);
  const service = createWalletService(db, {
    now: () => currentTime,
    claimRateLimitMillis: 0,
  });
  try {
    const results = await Promise.all([
      service.claimDailyReward(request(uid, { requestId: "wallet-race-request-a" })),
      service.claimDailyReward(request(uid, { requestId: "wallet-race-request-b" })),
    ]);
    assert.equal(results.filter((result) => result.claimed).length, 1);
    assert.equal(results.filter((result) => result.alreadyClaimed).length, 1);
    assert.equal((await service.getArenaAccount(request(uid))).wallet.coinsBalance, 50);
    assert.equal(
      (await db.collection("wallets").doc(uid).collection("transactions").get()).size,
      1
    );

    currentTime += 2 * DAY;
    const afterMissedDay = await service.claimDailyReward(
      request(uid, { requestId: "wallet-after-missed-day" })
    );
    assert.equal(afterMissedDay.streakDays, 1);
    assert.equal(afterMissedDay.rewardCoins, 50);
    assert.equal((await service.getArenaAccount(request(uid))).wallet.coinsBalance, 100);
  } finally {
    await cleanup(uid);
  }
});

test("claim rate limit is enforced without blocking a later legitimate mission claim", {
  timeout: 60_000,
}, async () => {
  const uid = `wallet-rate-limit-${Date.now()}`;
  let currentTime = Date.UTC(2026, 6, 1, 12);
  const service = createWalletService(db, {
    now: () => currentTime,
    claimRateLimitMillis: 1_000,
  });
  try {
    await service.claimDailyReward(request(uid, { requestId: "wallet-rate-daily-001" }));
    await assert.rejects(
      service.claimMissionReward(request(uid, {
        missionId: "daily_login",
        requestId: "wallet-rate-mission-001",
      })),
      (error) => error.code === "resource-exhausted"
    );
    currentTime += 1_001;
    const claim = await service.claimMissionReward(request(uid, {
      missionId: "daily_login",
      requestId: "wallet-rate-mission-002",
    }));
    assert.equal(claim.claimed, true);
  } finally {
    await cleanup(uid);
  }
});

test.after(async () => {
  await db.terminate();
});
