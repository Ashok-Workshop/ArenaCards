const test = require("node:test");
const assert = require("node:assert/strict");
const {
  INITIAL_RATING,
  MIN_WIN_RATE_MATCHES,
  advanceBracket,
  calculateRatingDeltas,
  compareLeaderboardEntries,
  createSingleEliminationBracket,
  deserializeBracket,
  serializeBracket,
  winPercentage,
} = require("./competitive-domain");

test("pairwise Elo rewards placement above expectation and penalizes it below expectation", () => {
  const players = [
    { uid: "winner", rating: INITIAL_RATING, position: 1 },
    { uid: "middle", rating: INITIAL_RATING, position: 2 },
    { uid: "last", rating: INITIAL_RATING, position: 3 },
  ];
  const deltas = calculateRatingDeltas(players);
  assert.ok(deltas.winner > 0);
  assert.equal(deltas.middle, 0);
  assert.ok(deltas.last < 0);
  assert.equal(
    calculateRatingDeltas([
      { uid: "favored", rating: 1_400, position: 2 },
      { uid: "underdog", rating: 600, position: 1 },
    ]).underdog > 0,
    true
  );
});

test("rating calculation supports two, three, and four placements and rejects invalid inputs", () => {
  for (const count of [2, 3, 4]) {
    const players = Array.from({ length: count }, (_, index) => ({
      uid: `p${index}`,
      rating: INITIAL_RATING,
      position: index + 1,
    }));
    assert.equal(Object.keys(calculateRatingDeltas(players)).length, count);
  }
  assert.throws(() => calculateRatingDeltas([{ uid: "only", rating: 1_000, position: 1 }]));
  assert.throws(() => calculateRatingDeltas([
    { uid: "same", rating: 1_000, position: 1 },
    { uid: "same", rating: 1_000, position: 2 },
  ]));
});

test("leaderboard ordering has deterministic metric and UID tie breaks", () => {
  const entries = [
    { uid: "z", rating: 1_000, wins: 9, winPercentage: 0.9 },
    { uid: "a", rating: 1_000, wins: 9, winPercentage: 0.9 },
    { uid: "b", rating: 1_000, wins: 10, winPercentage: 0.8 },
  ];
  assert.deepEqual(entries.sort(compareLeaderboardEntries("rating")).map(({ uid }) => uid), ["b", "a", "z"]);
  assert.equal(MIN_WIN_RATE_MATCHES, 10);
  assert.equal(winPercentage(5, 10), 0.5);
  assert.equal(winPercentage(0, 0), 0);
});

test("single-elimination bracket seeds deterministically and grants byes", () => {
  const participants = ["d", "b", "a", "c", "e"].map((uid, index) => ({
    uid,
    rating: [1_100, 1_300, 1_400, 1_200, 1_000][index],
  }));
  const bracket = createSingleEliminationBracket(participants);
  assert.equal(bracket.format, "SINGLE_ELIMINATION");
  assert.equal(bracket.rounds.length, 3);
  assert.deepEqual(bracket.rounds[0].map((match) => match.players), [
    ["a", null],
    ["d", "e"],
    ["b", null],
    ["c", null],
  ]);
  assert.equal(bracket.rounds[0][0].winnerUid, "a");
  assert.equal(bracket.rounds[1][0].winnerUid, "a");
  assert.deepEqual(bracket.rounds[1][1].players, ["b", "c"]);
});

test("bracket advancement accepts only the active pairing and auto-advances byes", () => {
  const bracket = createSingleEliminationBracket([
    { uid: "a", rating: 1_400 },
    { uid: "b", rating: 1_300 },
    { uid: "c", rating: 1_200 },
    { uid: "d", rating: 1_100 },
  ]);
  bracket.rounds[0][0].status = "ACTIVE";
  assert.throws(() => advanceBracket(bracket, "r1-m1", "c", "match-12345678"));
  const advanced = advanceBracket(bracket, "r1-m1", "a", "match-12345678");
  assert.equal(advanced.rounds[0][0].status, "COMPLETED");
  assert.equal(advanced.rounds[1][0].players[0], "a");
  assert.equal(advanced.rounds[1][0].players[1], null);
});

test("Firestore bracket serialization stores round arrays in a map and restores domain shape", () => {
  const bracket = createSingleEliminationBracket([
    { uid: "a", rating: 1_200 },
    { uid: "b", rating: 1_100 },
    { uid: "c", rating: 1_000 },
  ]);
  const stored = serializeBracket(bracket);
  assert.equal(Array.isArray(stored.rounds), false);
  assert.equal(Array.isArray(stored.rounds.round1), true);
  assert.deepEqual(deserializeBracket(stored), bracket);
});
