const test = require("node:test");
const assert = require("node:assert/strict");
const {
  ACHIEVEMENTS,
  DAILY_REWARD_COINS,
  MISSIONS,
  createWalletService,
  playerLevel,
  utcDateKey,
  utcDayStart,
} = require("./wallet-service");

test("daily calendar uses a UTC boundary independent of local timezone", () => {
  const beforeUtcMidnight = Date.parse("2026-03-01T23:59:59.999Z");
  const atUtcMidnight = Date.parse("2026-03-02T00:00:00.000Z");

  assert.equal(utcDateKey(beforeUtcMidnight), "2026-03-01");
  assert.equal(utcDateKey(atUtcMidnight), "2026-03-02");
  assert.equal(utcDayStart(atUtcMidnight), atUtcMidnight);
});

test("server progression levels remain separate from coin rewards", () => {
  assert.equal(playerLevel(0), 1);
  assert.equal(playerLevel(999), 1);
  assert.equal(playerLevel(1_000), 2);
  assert.deepEqual(DAILY_REWARD_COINS, [50, 75, 100, 150, 200, 250, 500]);
  assert.equal(MISSIONS.find((mission) => mission.id === "verified_online_match").enabled, false);
  assert.deepEqual(ACHIEVEMENTS.map((achievement) => achievement.targetValue), [3, 7]);
});

test("wallet callables reject unauthenticated, unverified, and forged requests", async () => {
  const service = createWalletService({});
  const request = (data, token = {}) => ({
    auth: {
      uid: "wallet-unit-user",
      token: {
        firebase: { sign_in_provider: "google.com" },
        ...token,
      },
    },
    data,
  });

  await assert.rejects(
    service.getArenaAccount({ data: {} }),
    (error) => error.code === "unauthenticated"
  );
  await assert.rejects(
    service.claimDailyReward(request({ requestId: "request-123" }, {
      firebase: { sign_in_provider: "password" },
      email_verified: false,
    })),
    (error) => error.code === "permission-denied"
  );
  await assert.rejects(
    service.claimDailyReward(request({ requestId: "request-123", rewardAmount: 999_999 })),
    (error) => error.code === "invalid-argument"
  );
  await assert.rejects(
    service.claimMissionReward(request({
      missionId: "verified_online_match",
      requestId: "request-123",
    })),
    (error) => error.code === "invalid-argument"
  );
});
