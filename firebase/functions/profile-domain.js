const RESERVED_USERNAMES = new Set([
  "admin",
  "administrator",
  "arenacards",
  "moderator",
  "null",
  "official",
  "root",
  "support",
  "system",
  "undefined",
]);

const USERNAME_PATTERN = /^[a-z0-9_]{3,20}$/;
const GAMER_ID_PATTERN = /^AR-[0-9]{6}$/;

function normalizeUsername(username) {
  return String(username ?? "").trim().toLowerCase();
}

function validateUsername(username) {
  const normalized = normalizeUsername(username);
  if (!USERNAME_PATTERN.test(normalized)) {
    return { valid: false, normalized, reason: "format" };
  }
  if (
    RESERVED_USERNAMES.has(normalized)
    || ["admin", "arenacards", "moderator", "official", "support"].some((prefix) =>
      normalized.startsWith(prefix))
  ) {
    return { valid: false, normalized, reason: "reserved" };
  }
  return { valid: true, normalized, reason: null };
}

function containsProfanity(username, filter) {
  return filter.isProfane(username.replace(/[_0-9]+/g, " "));
}

function isValidGamerId(gamerId) {
  return GAMER_ID_PATTERN.test(gamerId);
}

function formatGamerId(number) {
  if (!Number.isInteger(number) || number < 0 || number > 999999) {
    throw new RangeError("Gamer ID suffix must be a six-digit non-negative integer.");
  }
  return `AR-${String(number).padStart(6, "0")}`;
}

function playerLevel(experiencePoints) {
  return Math.floor(experiencePoints / 1000) + 1;
}

module.exports = {
  formatGamerId,
  isValidGamerId,
  containsProfanity,
  normalizeUsername,
  playerLevel,
  validateUsername,
};
