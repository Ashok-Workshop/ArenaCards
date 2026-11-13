const test = require("node:test");
const assert = require("node:assert/strict");
const {
  formatGamerId,
  containsProfanity,
  isValidGamerId,
  normalizeUsername,
  playerLevel,
  validateUsername,
} = require("./profile-domain");

test("username normalization is case-insensitive and trims whitespace", () => {
  assert.equal(normalizeUsername("  Arena_Player  "), "arena_player");
});

test("username moderation can reject profanity after normalization", () => {
  const filter = { isProfane: (value) => value.includes("blocked word") };
  assert.equal(containsProfanity("blocked_word", filter), true);
  assert.equal(containsProfanity("friendly_player", filter), false);
});

test("username validation accepts the supported character and length range", () => {
  assert.equal(validateUsername("Player_123").valid, true);
  assert.equal(validateUsername("ab").valid, false);
  assert.equal(validateUsername("player name").valid, false);
  assert.equal(validateUsername("admin").reason, "reserved");
});

test("Gamer IDs use the permanent six-digit AR format", () => {
  assert.equal(formatGamerId(827491), "AR-827491");
  assert.equal(isValidGamerId("AR-827491"), true);
  assert.equal(isValidGamerId("AR-82749"), false);
  assert.throws(() => formatGamerId(1_000_000), RangeError);
});

test("level is derived from trusted experience points", () => {
  assert.equal(playerLevel(0), 1);
  assert.equal(playerLevel(999), 1);
  assert.equal(playerLevel(1000), 2);
});
