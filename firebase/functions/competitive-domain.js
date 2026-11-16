const INITIAL_RATING = 1_000;
const RATING_K_FACTOR = 32;
const MIN_WIN_RATE_MATCHES = 10;

function assertValidPlayers(players) {
  if (!Array.isArray(players) || players.length < 2 || players.length > 4) {
    throw new TypeError("Competitive matches require two to four players.");
  }
  if (new Set(players.map((player) => player.uid)).size !== players.length) {
    throw new TypeError("Competitive match participants must be unique.");
  }
  if (players.some((player) => !Number.isSafeInteger(player.rating) || player.rating < 0)) {
    throw new TypeError("Competitive ratings must be non-negative integers.");
  }
  if (players.some((player) => !Number.isSafeInteger(player.position) || player.position < 1)) {
    throw new TypeError("Competitive placements must be positive integers.");
  }
}

function expectedScore(rating, opponentRating) {
  return 1 / (1 + (10 ** ((opponentRating - rating) / 400)));
}

function calculateRatingDeltas(players) {
  assertValidPlayers(players);
  const deltas = Object.fromEntries(players.map((player) => [player.uid, 0]));
  for (const player of players) {
    let expected = 0;
    let actual = 0;
    for (const opponent of players) {
      if (opponent.uid === player.uid) continue;
      expected += expectedScore(player.rating, opponent.rating);
      actual += player.position < opponent.position
        ? 1
        : player.position === opponent.position ? 0.5 : 0;
    }
    const opponents = players.length - 1;
    deltas[player.uid] = Math.round(
      RATING_K_FACTOR * ((actual - expected) / opponents)
    );
  }
  return deltas;
}

function winPercentage(wins, matches) {
  if (!Number.isSafeInteger(wins) || wins < 0
    || !Number.isSafeInteger(matches) || matches < 0) {
    throw new TypeError("Competitive match statistics are invalid.");
  }
  return matches === 0 ? 0 : wins / matches;
}

function compareLeaderboardEntries(category) {
  const metric = {
    rating: "rating",
    wins: "wins",
    winRate: "winPercentage",
    season: "seasonRating",
  }[category];
  if (!metric) throw new TypeError("Unsupported leaderboard category.");
  return (left, right) =>
    (right[metric] - left[metric])
    || (right.wins - left.wins)
    || left.uid.localeCompare(right.uid);
}

function orderTournamentSeeds(participants) {
  return [...participants].sort((left, right) =>
    (right.rating - left.rating)
    || left.uid.localeCompare(right.uid)
  );
}

function seededPositions(size) {
  let positions = [1, 2];
  while (positions.length < size) {
    const nextSize = positions.length * 2;
    positions = positions.flatMap((seed) => [seed, nextSize + 1 - seed]);
  }
  return positions;
}

function createSingleEliminationBracket(participants) {
  if (!Array.isArray(participants) || participants.length < 2 || participants.length > 64) {
    throw new TypeError("Single-elimination tournaments require two to 64 participants.");
  }
  if (new Set(participants.map((player) => player.uid)).size !== participants.length) {
    throw new TypeError("Tournament participants must be unique.");
  }
  const seeds = orderTournamentSeeds(participants);
  const bracketSize = 2 ** Math.ceil(Math.log2(seeds.length));
  const slots = Array(bracketSize).fill(null);
  const positions = seededPositions(bracketSize);
  for (let index = 0; index < seeds.length; index += 1) {
    slots[positions.indexOf(index + 1)] = seeds[index].uid;
  }

  const rounds = [];
  let currentSlots = slots;
  let roundNumber = 1;
  while (currentSlots.length > 1) {
    const matches = [];
    for (let index = 0; index < currentSlots.length; index += 2) {
      const players = [currentSlots[index], currentSlots[index + 1]];
      const isBye = players.filter(Boolean).length === 1;
      matches.push({
        matchId: `r${roundNumber}-m${matches.length + 1}`,
        round: roundNumber,
        players,
        status: isBye ? "COMPLETED" : "PENDING",
        winnerUid: isBye ? players.find(Boolean) : null,
        onlineMatchId: null,
        isBye,
      });
    }
    rounds.push(matches);
    currentSlots = Array.from({ length: matches.length }, () => null);
    roundNumber += 1;
  }

  for (let roundIndex = 0; roundIndex < rounds.length - 1; roundIndex += 1) {
    const matches = rounds[roundIndex];
    const nextRound = rounds[roundIndex + 1];
    for (let matchIndex = 0; matchIndex < matches.length; matchIndex += 1) {
      const match = matches[matchIndex];
      if (match.isBye) {
        const nextMatch = nextRound[Math.floor(matchIndex / 2)];
        nextMatch.players[matchIndex % 2] = match.winnerUid;
      }
    }
    for (const match of nextRound) {
      if (match.players.filter(Boolean).length === 1) {
        match.status = "COMPLETED";
        match.winnerUid = match.players.find(Boolean);
        match.isBye = true;
      }
    }
  }

  return {
    format: "SINGLE_ELIMINATION",
    rounds,
    championUid: null,
  };
}

function advanceBracket(bracket, completedMatchId, winnerUid, onlineMatchId) {
  const rounds = bracket.rounds.map((round) => round.map((match) => ({
    ...match,
    players: [...match.players],
  })));
  const match = rounds.flat().find((item) => item.matchId === completedMatchId);
  if (!match || match.status !== "ACTIVE" || !match.players.includes(winnerUid)) {
    throw new TypeError("Tournament result does not match an active bracket pairing.");
  }
  match.status = "COMPLETED";
  match.winnerUid = winnerUid;
  match.onlineMatchId = onlineMatchId;
  const roundMatches = rounds[match.round - 1];
  const matchIndex = roundMatches.indexOf(match);
  if (match.round === rounds.length) {
    return { ...bracket, rounds, championUid: winnerUid, isComplete: true };
  }

  const nextMatch = rounds[match.round][Math.floor(matchIndex / 2)];
  const slot = matchIndex % 2;
  if (nextMatch.players[slot] !== null && nextMatch.players[slot] !== undefined) {
    throw new TypeError("Tournament bracket slot has already been filled.");
  }
  nextMatch.players[slot] = winnerUid;
  if (nextMatch.players.every(Boolean)) {
    nextMatch.status = "PENDING";
    nextMatch.isBye = false;
  } else if (nextMatch.players.filter(Boolean).length === 1) {
    nextMatch.status = "COMPLETED";
    nextMatch.winnerUid = nextMatch.players.find(Boolean);
    nextMatch.isBye = true;
  }

  let current = nextMatch;
  while (current.isBye && current.round < rounds.length) {
    const next = rounds[current.round][Math.floor(
      rounds[current.round - 1].indexOf(current) / 2
    )];
    const currentIndex = rounds[current.round - 1].indexOf(current);
    const nextSlot = currentIndex % 2;
    if (next.players[nextSlot] !== null && next.players[nextSlot] !== undefined) {
      break;
    }
    next.players[nextSlot] = current.winnerUid;
    if (next.players.every(Boolean)) {
      next.status = "PENDING";
      next.isBye = false;
      break;
    }
    next.status = "COMPLETED";
    next.winnerUid = next.players.find(Boolean);
    next.isBye = true;
    current = next;
  }

  if (rounds.at(-1)?.[0]?.status === "COMPLETED") {
    return { ...bracket, rounds, championUid: rounds.at(-1)[0].winnerUid, isComplete: true };
  }
  return { ...bracket, rounds, championUid: null, isComplete: false };
}

function serializeBracket(bracket) {
  return {
    ...bracket,
    rounds: Object.fromEntries(bracket.rounds.map((round, index) => [
      `round${index + 1}`,
      round,
    ])),
  };
}

function deserializeBracket(bracket) {
  if (Array.isArray(bracket.rounds)) return bracket;
  const roundKeys = Object.keys(bracket.rounds || {}).sort(
    (left, right) => Number(left.replace("round", "")) - Number(right.replace("round", ""))
  );
  return { ...bracket, rounds: roundKeys.map((key) => bracket.rounds[key]) };
}

module.exports = {
  INITIAL_RATING,
  MIN_WIN_RATE_MATCHES,
  RATING_K_FACTOR,
  advanceBracket,
  calculateRatingDeltas,
  compareLeaderboardEntries,
  createSingleEliminationBracket,
  orderTournamentSeeds,
  deserializeBracket,
  serializeBracket,
  winPercentage,
};
