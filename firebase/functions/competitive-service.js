const { HttpsError } = require("firebase-functions/v2/https");
const { FieldPath, FieldValue } = require("./firestore-client");
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

const MAX_PAGE_SIZE = 50;
const MAX_FRIENDS = 100;
const MAX_TOURNAMENT_PARTICIPANTS = 64;
const DEFAULT_SEASON_REWARDS = Object.freeze([
  { place: 1, coins: 200, xp: 300 },
  { place: 2, coins: 100, xp: 150 },
  { place: 3, coins: 50, xp: 75 },
]);
const COMPETITIVE_ACHIEVEMENTS = Object.freeze([
  { id: "competitive_first_win", title: "First Competitive Victory", xp: 100, condition: (old, next) => old.wins === 0 && next.wins > 0 },
  { id: "competitive_matches_10", title: "Ten Verified Matches", xp: 100, condition: (old, next) => old.verifiedMatches < 10 && next.verifiedMatches >= 10 },
  { id: "competitive_wins_10", title: "Ten Competitive Wins", xp: 150, condition: (old, next) => old.wins < 10 && next.wins >= 10 },
  { id: "competitive_win_streak_3", title: "Three-Game Win Streak", xp: 100, condition: (old, next) => old.bestWinStreak < 3 && next.bestWinStreak >= 3 },
]);

function createCompetitiveService(db, options = {}) {
  const now = options.now || Date.now;
  const createTournamentBracket = options.createBracket || createSingleEliminationBracket;

  function requireUid(request) {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in to access competitive play.");
    if (request.auth.token.firebase?.sign_in_provider === "password"
      && request.auth.token.email_verified !== true) {
      throw new HttpsError("permission-denied", "Verify your email before competitive play.");
    }
    return uid;
  }

  function requireAdmin(request) {
    const uid = requireUid(request);
    if (request.auth.token.admin !== true) {
      throw new HttpsError("permission-denied", "An administrator must perform this operation.");
    }
    return uid;
  }

  function validateData(data, keys) {
    if (!data || typeof data !== "object" || Array.isArray(data)
      || Object.keys(data).some((key) => !keys.includes(key))) {
      throw new HttpsError("invalid-argument", "Request contains invalid or unsupported fields.");
    }
  }

  function validateId(value, label) {
    if (typeof value !== "string" || !/^[A-Za-z0-9_-]{1,80}$/.test(value)) {
      throw new HttpsError("invalid-argument", `A valid ${label} is required.`);
    }
    return value;
  }

  function pageSize(value) {
    if (value === undefined) return 20;
    if (!Number.isSafeInteger(value) || value < 1 || value > MAX_PAGE_SIZE) {
      throw new HttpsError("invalid-argument", "Page size must be from 1 to 50.");
    }
    return value;
  }

  function statsFrom(snapshot) {
    if (!snapshot.exists) {
      return {
        rating: INITIAL_RATING,
        verifiedMatches: 0,
        wins: 0,
        losses: 0,
        winPercentage: 0,
        winRateEligible: false,
        bestRating: INITIAL_RATING,
        bestRank: null,
        winStreak: 0,
        bestWinStreak: 0,
        isPublic: false,
      };
    }
    const data = snapshot.data();
    const stats = {
      rating: data.rating,
      verifiedMatches: data.verifiedMatches,
      wins: data.wins,
      losses: data.losses,
      winPercentage: data.winPercentage,
      winRateEligible: data.winRateEligible === true,
      bestRating: data.bestRating,
      bestRank: data.bestRank ?? null,
      winStreak: data.winStreak ?? 0,
      bestWinStreak: data.bestWinStreak ?? 0,
      isPublic: data.isPublic === true,
    };
    if (!Number.isSafeInteger(stats.rating) || stats.rating < 0
      || !Number.isSafeInteger(stats.verifiedMatches) || stats.verifiedMatches < 0
      || !Number.isSafeInteger(stats.wins) || stats.wins < 0
      || !Number.isSafeInteger(stats.losses) || stats.losses < 0
      || !Number.isFinite(stats.winPercentage) || stats.winPercentage < 0
      || stats.winPercentage > 1) {
      throw new HttpsError("internal", "Competitive statistics failed validation.");
    }
    return stats;
  }

  function currentSeasonRef() {
    return db.collection("competitiveSeasonState").doc("current");
  }

  function seasonRef(seasonId) {
    return db.collection("competitiveSeasons").doc(seasonId);
  }

  function seasonStatsRef(seasonId, uid) {
    return db.collection("competitiveSeasons").doc(seasonId)
      .collection("players").doc(uid);
  }

  function seasonEntryRef(seasonId, uid) {
    return db.collection("seasonLeaderboardEntries").doc(`${seasonId}_${uid}`);
  }

  function tournamentRef(tournamentId) {
    return db.collection("tournaments").doc(tournamentId);
  }

  function notificationRef(uid, notificationId) {
    return db.collection("inAppNotifications").doc(uid)
      .collection("items").doc(notificationId);
  }

  function categoryQuery(category, seasonId) {
    if (category === "season") {
      if (!seasonId) throw new HttpsError("failed-precondition", "There is no active season.");
      return leaderboardBaseQuery(category, seasonId)
        .orderBy("seasonRating", "desc")
        .orderBy("seasonPoints", "desc")
        .orderBy(FieldPath.documentId(), "asc");
    }
    const query = leaderboardBaseQuery(category);
    if (category === "rating") {
      return query.orderBy("rating", "desc").orderBy("wins", "desc")
        .orderBy(FieldPath.documentId(), "asc");
    }
    if (category === "wins") {
      return query.orderBy("wins", "desc").orderBy("rating", "desc")
        .orderBy(FieldPath.documentId(), "asc");
    }
    if (category === "winRate") {
      return query.where("winRateEligible", "==", true)
        .orderBy("winPercentage", "desc").orderBy("verifiedMatches", "desc")
        .orderBy(FieldPath.documentId(), "asc");
    }
    throw new HttpsError("invalid-argument", "Choose rating, wins, winRate, or season.");
  }

  function leaderboardBaseQuery(category, seasonId) {
    if (category === "season") {
      if (!seasonId) throw new HttpsError("failed-precondition", "There is no active season.");
      return db.collection("seasonLeaderboardEntries")
        .where("seasonId", "==", seasonId)
        .where("isPublic", "==", true);
    }
    let query = db.collection("competitiveProfiles").where("isPublic", "==", true);
    if (category === "winRate") query = query.where("winRateEligible", "==", true);
    return query;
  }

  async function rankFor(uid, category, primary, secondary, seasonId) {
    const primaryField = category === "rating" ? "rating"
      : category === "wins" ? "wins"
        : category === "winRate" ? "winPercentage" : "seasonRating";
    const secondaryField = category === "rating" ? "wins"
      : category === "wins" ? "rating"
        : category === "winRate" ? "verifiedMatches" : "seasonPoints";
    const documentId = category === "season" ? `${seasonId}_${uid}` : uid;
    const base = leaderboardBaseQuery(category, seasonId);
    const [higherPrimary, tiedPrimaryHigherSecondary, exactTieBeforeUid] = await Promise.all([
      base.where(primaryField, ">", primary).count().get(),
      base.where(primaryField, "==", primary)
        .where(secondaryField, ">", secondary).count().get(),
      base.where(primaryField, "==", primary)
        .where(secondaryField, "==", secondary)
        .where(FieldPath.documentId(), "<", documentId).count().get(),
    ]);
    return 1 + higherPrimary.data().count
      + tiedPrimaryHigherSecondary.data().count
      + exactTieBeforeUid.data().count;
  }

  function cursorValues(cursor, category) {
    if (cursor === undefined || cursor === null) return null;
    if (!cursor || typeof cursor.uid !== "string" || !Number.isFinite(cursor.primary)) {
      throw new HttpsError("invalid-argument", "Leaderboard cursor is invalid.");
    }
    const secondary = category === "rating" || category === "wins"
      ? cursor.secondary
      : category === "winRate" ? cursor.secondary
        : cursor.secondary;
    if (!Number.isFinite(secondary)) {
      throw new HttpsError("invalid-argument", "Leaderboard cursor is invalid.");
    }
    return [cursor.primary, secondary, cursor.documentId || cursor.uid];
  }

  function primaryMetric(entry, category) {
    return category === "rating" ? entry.rating
      : category === "wins" ? entry.wins
        : category === "winRate" ? entry.winPercentage
          : entry.seasonRating;
  }

  function secondaryMetric(entry, category) {
    return category === "rating" || category === "season"
      ? category === "season" ? entry.seasonPoints : entry.wins
      : category === "wins" ? entry.rating : entry.verifiedMatches;
  }

  function toCursor(entry, category) {
    return {
      primary: primaryMetric(entry, category),
      secondary: secondaryMetric(entry, category),
      uid: entry.uid,
      documentId: entry.documentId || entry.uid,
    };
  }

  async function getActiveSeason() {
    const pointer = await currentSeasonRef().get();
    if (!pointer.exists || !pointer.get("seasonId")) return null;
    const snapshot = await seasonRef(pointer.get("seasonId")).get();
    if (!snapshot.exists || snapshot.get("status") !== "ACTIVE") return null;
    return { id: snapshot.id, ...snapshot.data() };
  }

  async function getCompetitiveLeaderboard(request) {
    validateData(request.data, ["category", "seasonId", "pageSize", "cursor"]);
    const uid = requireUid(request);
    const category = request.data.category || "rating";
    if (!["rating", "wins", "winRate", "season"].includes(category)) {
      throw new HttpsError("invalid-argument", "Choose rating, wins, winRate, or season.");
    }
    const size = pageSize(request.data.pageSize);
    const activeSeason = category === "season" ? await getActiveSeason() : null;
    const seasonId = category === "season"
      ? (request.data.seasonId || activeSeason?.id)
      : undefined;
    const query = categoryQuery(category, seasonId);
    const cursor = cursorValues(request.data.cursor, category);
    let paged = query;
    if (cursor) paged = paged.startAfter(...cursor);
    const snapshot = await paged.limit(size + 1).get();
    const docs = snapshot.docs.slice(0, size);
    const uids = docs.map((doc) => doc.get("uid") || doc.id.replace(`${seasonId}_`, ""));
    const profiles = await Promise.all(uids.map((playerUid) =>
      db.collection("publicPlayers").doc(playerUid).get()));
    const entries = docs.flatMap((doc, index) => {
      const profile = profiles[index];
      if (!profile.exists || profile.get("profileVisibility") !== "public") return [];
      const record = doc.data();
      const playerUid = uids[index];
      return [{
        uid: playerUid,
        documentId: doc.id,
        gamerId: profile.get("gamerId"),
        username: profile.get("username"),
        displayName: profile.get("displayName"),
        avatarId: profile.get("avatarId"),
        rating: record.rating ?? INITIAL_RATING,
        seasonRating: record.seasonRating ?? INITIAL_RATING,
        seasonPoints: record.seasonPoints ?? 0,
        verifiedMatches: record.verifiedMatches ?? 0,
        wins: record.wins ?? 0,
        losses: record.losses ?? 0,
        winPercentage: record.winPercentage ?? 0,
        queryPosition: index,
        isCurrentPlayer: playerUid === uid,
      }];
    });
    const lastDoc = docs.at(-1);
    const currentProfile = await db.collection("competitiveProfiles").doc(uid).get();
    const ownStats = statsFrom(currentProfile);
    const ownSeason = category === "season" && seasonId
      ? await seasonStatsRef(seasonId, uid).get()
      : null;
    const ownMetricStats = category === "season" ? {
      seasonRating: ownSeason?.exists ? ownSeason.get("seasonRating") : INITIAL_RATING,
      seasonPoints: ownSeason?.exists ? ownSeason.get("seasonPoints") : 0,
    } : ownStats;
    const ownRank = ownStats.isPublic
      && (category !== "winRate" || ownStats.verifiedMatches >= MIN_WIN_RATE_MATCHES)
      ? await rankFor(
        uid,
        category,
        primaryMetric(ownMetricStats, category),
        secondaryMetric({ ...ownStats, ...ownMetricStats }, category),
        seasonId
      )
      : null;
    const first = entries[0];
    if (first) {
      const firstRank = await rankFor(
        first.uid,
        category,
        primaryMetric(first, category),
        secondaryMetric(first, category),
        seasonId
      );
      entries.forEach((entry) => {
        entry.rank = firstRank + entry.queryPosition;
        delete entry.queryPosition;
      });
    }
    return {
      category,
      seasonId: seasonId || null,
      entries,
      currentPlayer: {
        uid,
        ...ownStats,
        rank: ownRank,
      },
      lastUpdatedAtMillis: now(),
      nextCursor: snapshot.docs.length > size && lastDoc
        ? toCursor({
          uid: uids[size - 1],
          documentId: lastDoc.id,
          rating: lastDoc.get("rating") ?? INITIAL_RATING,
          wins: lastDoc.get("wins") ?? 0,
          winPercentage: lastDoc.get("winPercentage") ?? 0,
          verifiedMatches: lastDoc.get("verifiedMatches") ?? 0,
          seasonRating: lastDoc.get("seasonRating") ?? INITIAL_RATING,
          seasonPoints: lastDoc.get("seasonPoints") ?? 0,
        }, category)
        : null,
    };
  }

  async function getMyCompetitiveProfile(request) {
    validateData(request.data, []);
    const uid = requireUid(request);
    const profileRef = db.collection("competitiveProfiles").doc(uid);
    const [statsSnapshot, userSnapshot, activeSeason] = await Promise.all([
      profileRef.get(),
      db.collection("users").doc(uid).get(),
      getActiveSeason(),
    ]);
    const stats = statsFrom(statsSnapshot);
    let seasonStats = null;
    if (activeSeason) {
      const snapshot = await seasonStatsRef(activeSeason.id, uid).get();
      seasonStats = snapshot.exists ? snapshot.data() : {
        seasonRating: INITIAL_RATING,
        seasonPoints: 0,
        verifiedMatches: 0,
        wins: 0,
        losses: 0,
      };
    }
    const achievements = await db.collection("competitiveAchievements").doc(uid)
      .collection("unlocks").get();
    return {
      uid,
      gamerId: userSnapshot.get("gamerId") || null,
      displayName: userSnapshot.get("displayName") || null,
      avatarId: userSnapshot.get("avatarId") || null,
      ...stats,
      season: activeSeason ? {
        id: activeSeason.id,
        title: activeSeason.title,
        startsAtMillis: activeSeason.startsAtMillis,
        endsAtMillis: activeSeason.endsAtMillis,
        stats: seasonStats,
      } : null,
      achievements: achievements.docs.map((doc) => ({
        id: doc.id,
        title: doc.get("title"),
        unlockedAtMillis: timestampMillis(doc.get("unlockedAt")),
      })),
      tournamentParticipation: (await db.collection("tournamentParticipants").doc(uid)
        .collection("entries").orderBy("updatedAt", "desc").limit(20).get())
        .docs.map((doc) => ({
          tournamentId: doc.get("tournamentId"),
          tournamentName: doc.get("tournamentName"),
          status: doc.get("status"),
          place: doc.get("place") ?? null,
        })),
    };
  }

  async function getFriendsLeaderboard(request) {
    validateData(request.data, ["category"]);
    const uid = requireUid(request);
    const category = request.data.category || "rating";
    if (!["rating", "wins", "winRate", "season"].includes(category)) {
      throw new HttpsError("invalid-argument", "Choose rating, wins, winRate, or season.");
    }
    const [friendSnapshot, ownProfile, activeSeason] = await Promise.all([
      db.collection("friendships").doc(uid).collection("friends").limit(MAX_FRIENDS).get(),
      db.collection("competitiveProfiles").doc(uid).get(),
      category === "season" ? getActiveSeason() : Promise.resolve(null),
    ]);
    const seasonId = category === "season" ? activeSeason?.id : null;
    if (category === "season" && !seasonId) {
      throw new HttpsError("failed-precondition", "There is no active season.");
    }
    const friendUids = [...new Set([uid, ...friendSnapshot.docs.map((doc) => doc.id)])];
    const blockRefs = friendUids.filter((friendUid) => friendUid !== uid).flatMap((friendUid) => [
      db.collection("userBlocks").doc(uid).collection("users").doc(friendUid),
      db.collection("userBlocks").doc(friendUid).collection("users").doc(uid),
    ]);
    const blockDocs = blockRefs.length ? await db.getAll(...blockRefs) : [];
    const blocked = new Set();
    friendUids.filter((friendUid) => friendUid !== uid).forEach((friendUid, index) => {
      if (blockDocs[index * 2].exists || blockDocs[index * 2 + 1].exists) blocked.add(friendUid);
    });
    const eligibleUids = friendUids.filter((friendUid) => friendUid === uid || !blocked.has(friendUid));
    const [statsDocs, publicDocs, seasonDocs] = await Promise.all([
      db.getAll(...eligibleUids.map((friendUid) => db.collection("competitiveProfiles").doc(friendUid))),
      db.getAll(...eligibleUids.map((friendUid) => db.collection("publicPlayers").doc(friendUid))),
      seasonId
        ? db.getAll(...eligibleUids.map((friendUid) => seasonStatsRef(seasonId, friendUid)))
        : Promise.resolve([]),
    ]);
    const entries = eligibleUids.flatMap((friendUid, index) => {
      const publicProfile = publicDocs[index];
      if (friendUid !== uid && (!publicProfile.exists
        || publicProfile.get("profileVisibility") !== "public")) return [];
      const stats = statsFrom(statsDocs[index]);
      if (category === "winRate" && stats.verifiedMatches < MIN_WIN_RATE_MATCHES) return [];
      const season = seasonDocs[index]?.exists ? seasonDocs[index].data() : {
        seasonRating: INITIAL_RATING,
        seasonPoints: 0,
        verifiedMatches: 0,
        wins: 0,
        losses: 0,
      };
      return [{
        uid: friendUid,
        gamerId: publicProfile.get("gamerId") || "",
        username: publicProfile.get("username") || "",
        displayName: publicProfile.get("displayName") || "Private player",
        avatarId: publicProfile.get("avatarId") || "ember",
        rating: stats.rating,
        seasonRating: season.seasonRating,
        seasonPoints: season.seasonPoints,
        verifiedMatches: stats.verifiedMatches,
        wins: stats.wins,
        losses: stats.losses,
        winPercentage: stats.winPercentage,
        isCurrentPlayer: friendUid === uid,
      }];
    });
    const ordered = entries.sort(compareLeaderboardEntries(category));
    return {
      category,
      seasonId,
      entries: ordered.map((entry, index) => ({ ...entry, rank: index + 1 })),
      currentPlayer: ordered.find((entry) => entry.uid === uid)
        ? { ...ownProfile.data(), rank: ordered.findIndex((entry) => entry.uid === uid) + 1 }
        : { ...statsFrom(ownProfile), uid, rank: null },
      lastUpdatedAtMillis: now(),
    };
  }

  async function createSeason(request) {
    validateData(request.data, [
      "seasonId", "title", "startsAtMillis", "endsAtMillis", "rewards",
    ]);
    requireAdmin(request);
    const seasonId = validateId(request.data.seasonId, "season ID");
    const title = typeof request.data.title === "string" ? request.data.title.trim() : "";
    const startsAtMillis = request.data.startsAtMillis;
    const endsAtMillis = request.data.endsAtMillis;
    if (!title || title.length > 60 || !Number.isSafeInteger(startsAtMillis)
      || !Number.isSafeInteger(endsAtMillis) || startsAtMillis >= endsAtMillis
      || endsAtMillis <= now()) {
      throw new HttpsError("invalid-argument", "Season title and future UTC boundaries are required.");
    }
    const rewards = validateRewards(request.data.rewards || DEFAULT_SEASON_REWARDS);
    const ref = seasonRef(seasonId);
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(ref);
      if (snapshot.exists) throw new HttpsError("already-exists", "That season already exists.");
      transaction.create(ref, {
        seasonId,
        title,
        startsAtMillis,
        endsAtMillis,
        rewards,
        status: "SCHEDULED",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    return { seasonId, status: "SCHEDULED" };
  }

  async function activateSeason(request) {
    validateData(request.data, ["seasonId"]);
    requireAdmin(request);
    const seasonId = validateId(request.data.seasonId, "season ID");
    const ref = seasonRef(seasonId);
    const pointerRef = currentSeasonRef();
    await db.runTransaction(async (transaction) => {
      const [season, pointer] = await Promise.all([
        transaction.get(ref),
        transaction.get(pointerRef),
      ]);
      if (!season.exists || season.get("status") !== "SCHEDULED") {
        throw new HttpsError("failed-precondition", "The season is not scheduled.");
      }
      if (season.get("startsAtMillis") > now() || season.get("endsAtMillis") <= now()) {
        throw new HttpsError("failed-precondition", "The season is outside its UTC activation window.");
      }
      if (pointer.exists && pointer.get("seasonId") !== seasonId) {
        const oldSeason = await transaction.get(seasonRef(pointer.get("seasonId")));
        if (oldSeason.exists && oldSeason.get("status") === "ACTIVE") {
          throw new HttpsError("failed-precondition", "Finalize the current season before activating another.");
        }
      }
      transaction.update(ref, { status: "ACTIVE", updatedAt: FieldValue.serverTimestamp() });
      transaction.set(pointerRef, { seasonId, updatedAt: FieldValue.serverTimestamp() });
    });
    return { seasonId, status: "ACTIVE" };
  }

  async function finalizeSeasonById(seasonId) {
    const ref = seasonRef(seasonId);
    const finalizationRef = db.collection("competitiveSeasonFinalizations").doc(seasonId);
    const season = await ref.get();
    if (!season.exists || season.get("status") !== "ACTIVE"
      || season.get("endsAtMillis") > now()) return { finalized: false };
    const resultsQuery = db.collection("seasonLeaderboardEntries")
      .where("seasonId", "==", seasonId)
      .orderBy("seasonRating", "desc")
      .orderBy("seasonPoints", "desc")
      .orderBy("uid", "asc")
      .limit(50);
    return db.runTransaction(async (transaction) => {
      const [seasonSnapshot, pointerSnapshot, previousFinalization, resultsSnapshot] =
        await Promise.all([
          transaction.get(ref),
          transaction.get(currentSeasonRef()),
          transaction.get(finalizationRef),
          transaction.get(resultsQuery),
        ]);
      if (previousFinalization.exists) return { finalized: false, alreadyFinalized: true };
      if (!seasonSnapshot.exists || seasonSnapshot.get("status") !== "ACTIVE"
        || seasonSnapshot.get("endsAtMillis") > now()) {
        return { finalized: false };
      }
      const ranked = resultsSnapshot.docs.map((doc, index) => ({
        ref: doc.ref,
        uid: doc.get("uid"),
        rank: index + 1,
        stats: doc.data(),
      }));
      const rewards = seasonSnapshot.get("rewards") || [];
      const payable = ranked.map((entry) => ({
        ...entry,
        reward: rewards.find((item) => item.place === entry.rank)
          || { place: entry.rank, coins: 0, xp: 0 },
      })).filter((entry) => entry.reward.coins > 0 || entry.reward.xp > 0);
      const accountRefs = payable.flatMap(({ uid }) => [
        db.collection("wallets").doc(uid),
        db.collection("progression").doc(uid),
        db.collection("users").doc(uid),
      ]);
      const accounts = accountRefs.length
        ? await Promise.all(accountRefs.map((accountRef) => transaction.get(accountRef)))
        : [];
      const plans = payable.map((entry, index) => {
        const [walletSnapshot, progressionSnapshot, userSnapshot] =
          accounts.slice(index * 3, index * 3 + 3);
        const wallet = walletSnapshot.exists ? walletSnapshot.data() : {
          coinsBalance: 0,
          totalCoinsEarned: 0,
          totalCoinsSpent: 0,
        };
        if (!Number.isSafeInteger(wallet.coinsBalance) || wallet.coinsBalance < 0
          || !Number.isSafeInteger(wallet.totalCoinsEarned) || wallet.totalCoinsEarned < 0
          || !Number.isSafeInteger(wallet.totalCoinsSpent) || wallet.totalCoinsSpent < 0
          || wallet.totalCoinsEarned - wallet.totalCoinsSpent !== wallet.coinsBalance) {
          throw new HttpsError("internal", "Wallet integrity check failed during season finalization.");
        }
        const currentXp = progressionSnapshot.exists
          ? progressionSnapshot.get("experiencePoints")
          : userSnapshot.get("experiencePoints") || 0;
        if (!Number.isSafeInteger(currentXp) || currentXp < 0) {
          throw new HttpsError("internal", "Progression integrity check failed during season finalization.");
        }
        return {
          ...entry,
          wallet,
          progressionSnapshot,
          currentXp,
          newBalance: wallet.coinsBalance + entry.reward.coins,
          newTotalEarned: wallet.totalCoinsEarned + entry.reward.coins,
          newXp: currentXp + entry.reward.xp,
        };
      });

      const timestamp = FieldValue.serverTimestamp();
      transaction.update(ref, {
        status: "COMPLETED",
        finalizedAt: timestamp,
        updatedAt: timestamp,
      });
      transaction.create(finalizationRef, {
        seasonId,
        participantCount: resultsSnapshot.size,
        finalizedAt: timestamp,
      });
      if (pointerSnapshot.exists && pointerSnapshot.get("seasonId") === seasonId) {
        transaction.delete(currentSeasonRef());
      }
      ranked.forEach((entry) => {
        transaction.set(entry.ref, { finalRank: entry.rank, updatedAt: timestamp }, { merge: true });
        transaction.create(ref.collection("finalResults").doc(entry.uid), {
          uid: entry.uid,
          rank: entry.rank,
          rating: entry.stats.seasonRating,
          points: entry.stats.seasonPoints,
          wins: entry.stats.wins,
          matches: entry.stats.verifiedMatches,
          createdAt: timestamp,
        });
        transaction.set(notificationRef(entry.uid, `season_completed_${seasonId}`), {
          type: "SEASON_COMPLETED",
          actorUid: "system",
          data: { seasonId, title: seasonSnapshot.get("title"), rank: entry.rank },
          createdAt: timestamp,
          readAt: null,
        });
      });
      plans.forEach((plan) => {
        const walletRef = db.collection("wallets").doc(plan.uid);
        const progressionRef = db.collection("progression").doc(plan.uid);
        const eventId = `season_${seasonId}`;
        if (plan.reward.coins > 0) {
          transaction.set(walletRef, {
            coinsBalance: plan.newBalance,
            totalCoinsEarned: plan.newTotalEarned,
            totalCoinsSpent: plan.wallet.totalCoinsSpent,
            createdAt: plan.wallet.createdAt || timestamp,
            updatedAt: timestamp,
          }, { merge: true });
          transaction.create(walletRef.collection("transactions").doc(eventId), {
            transactionId: eventId,
            uid: plan.uid,
            type: "SEASON_REWARD",
            amount: plan.reward.coins,
            balanceBefore: plan.wallet.coinsBalance,
            balanceAfter: plan.newBalance,
            source: "competitive_season",
            referenceId: seasonId,
            idempotencyKey: seasonId,
            timestamp,
            status: "COMMITTED",
          });
        }
        if (plan.reward.xp > 0) {
          transaction.set(progressionRef, {
            experiencePoints: plan.newXp,
            level: Math.floor(plan.newXp / 1_000) + 1,
            totalXpEarned: (plan.progressionSnapshot.get("totalXpEarned")
              || plan.currentXp) + plan.reward.xp,
            updatedAt: timestamp,
            ...(plan.progressionSnapshot.exists ? {} : { createdAt: timestamp }),
          }, { merge: true });
          transaction.create(progressionRef.collection("experienceEvents").doc(eventId), {
            eventId,
            type: "SEASON_REWARD",
            amount: plan.reward.xp,
            previousExperiencePoints: plan.currentXp,
            newExperiencePoints: plan.newXp,
            source: "competitive_season",
            referenceId: seasonId,
            timestamp,
            status: "COMMITTED",
          });
        }
      });
      return { finalized: true, participantCount: ranked.length };
    });
  }

  async function finalizeSeason(request) {
    validateData(request.data, ["seasonId"]);
    requireAdmin(request);
    const seasonId = validateId(request.data.seasonId, "season ID");
    const season = await seasonRef(seasonId).get();
    if (!season.exists) throw new HttpsError("not-found", "Season was not found.");
    if (season.get("endsAtMillis") > now()) {
      throw new HttpsError("failed-precondition", "A season cannot be finalized before its end time.");
    }
    return finalizeSeasonById(seasonId);
  }

  async function finalizeDueSeasons() {
    const snapshot = await db.collection("competitiveSeasons")
      .where("status", "==", "ACTIVE")
      .where("endsAtMillis", "<=", now())
      .limit(5)
      .get();
    const results = [];
    for (const season of snapshot.docs) {
      results.push({
        seasonId: season.id,
        ...await finalizeSeasonById(season.id),
      });
    }
    return { finalizedCount: results.filter((result) => result.finalized).length, results };
  }

  async function listSeasonHistory(request) {
    validateData(request.data, ["pageSize"]);
    requireUid(request);
    const size = pageSize(request.data.pageSize);
    const snapshot = await db.collection("competitiveSeasons")
      .where("status", "==", "COMPLETED")
      .orderBy("endsAtMillis", "desc")
      .limit(size)
      .get();
    return {
      seasons: snapshot.docs.map((doc) => ({
        seasonId: doc.id,
        title: doc.get("title"),
        startsAtMillis: doc.get("startsAtMillis"),
        endsAtMillis: doc.get("endsAtMillis"),
      })),
    };
  }

  async function listTournaments(request) {
    validateData(request.data, ["pageSize", "cursor"]);
    const uid = requireUid(request);
    const size = pageSize(request.data.pageSize);
    let query = db.collection("tournaments")
      .orderBy("registrationDeadlineMillis", "desc")
      .orderBy(FieldPath.documentId(), "asc")
      .limit(size + 1);
    if (request.data.cursor) {
      const cursor = request.data.cursor;
      if (!Number.isSafeInteger(cursor.deadlineMillis) || typeof cursor.tournamentId !== "string") {
        throw new HttpsError("invalid-argument", "Tournament cursor is invalid.");
      }
      query = query.startAfter(cursor.deadlineMillis, cursor.tournamentId);
    }
    const snapshot = await query.get();
    const docs = snapshot.docs.slice(0, size);
    const registrations = await Promise.all(docs.map((doc) =>
      db.collection("tournamentParticipants").doc(uid).collection("entries").doc(doc.id).get()));
    return {
      tournaments: docs.map((doc, index) => ({
        tournamentId: doc.id,
        title: doc.get("title"),
        description: doc.get("description"),
        status: doc.get("status"),
        format: doc.get("format"),
        participantCount: doc.get("participantCount"),
        maxParticipants: doc.get("maxParticipants"),
        registrationDeadlineMillis: doc.get("registrationDeadlineMillis"),
        startsAtMillis: doc.get("startsAtMillis"),
        rewards: doc.get("rewards"),
        isRegistered: registrations[index].exists,
      })),
      nextCursor: snapshot.docs.length > size && docs.length
        ? {
          deadlineMillis: docs.at(-1).get("registrationDeadlineMillis"),
          tournamentId: docs.at(-1).id,
        }
        : null,
    };
  }

  async function createTournament(request) {
    validateData(request.data, [
      "tournamentId", "title", "description", "format", "maxParticipants",
      "registrationDeadlineMillis", "startsAtMillis", "rewards",
    ]);
    requireAdmin(request);
    const tournamentId = validateId(request.data.tournamentId, "tournament ID");
    const title = typeof request.data.title === "string" ? request.data.title.trim() : "";
    const description = typeof request.data.description === "string"
      ? request.data.description.trim()
      : "";
    const maxParticipants = request.data.maxParticipants;
    const deadline = request.data.registrationDeadlineMillis;
    const startsAt = request.data.startsAtMillis;
    if (request.data.format !== "SINGLE_ELIMINATION") {
      throw new HttpsError("invalid-argument", "Only single elimination is currently supported.");
    }
    if (!title || title.length > 60 || description.length > 300
      || !Number.isSafeInteger(maxParticipants) || maxParticipants < 2
      || maxParticipants > MAX_TOURNAMENT_PARTICIPANTS || !Number.isSafeInteger(deadline)
      || !Number.isSafeInteger(startsAt) || deadline <= now() || startsAt < deadline) {
      throw new HttpsError("invalid-argument", "Tournament configuration is invalid.");
    }
    const rewards = validateTournamentRewards(request.data.rewards);
    const ref = tournamentRef(tournamentId);
    await db.runTransaction(async (transaction) => {
      const existing = await transaction.get(ref);
      if (existing.exists) throw new HttpsError("already-exists", "That tournament already exists.");
      transaction.create(ref, {
        tournamentId,
        title,
        description,
        format: "SINGLE_ELIMINATION",
        status: "OPEN",
        maxParticipants,
        participantCount: 0,
        registrationDeadlineMillis: deadline,
        startsAtMillis: startsAt,
        rewards,
        participants: [],
        bracket: null,
        championUid: null,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    return { tournamentId, status: "OPEN" };
  }

  async function registerTournament(request) {
    validateData(request.data, ["tournamentId"]);
    const uid = requireUid(request);
    const tournamentId = validateId(request.data.tournamentId, "tournament ID");
    const tournament = tournamentRef(tournamentId);
    const participant = db.collection("tournamentParticipants").doc(uid)
      .collection("entries").doc(tournamentId);
    const profile = db.collection("users").doc(uid);
    const notification = notificationRef(uid, `tournament_registered_${tournamentId}`);
    await db.runTransaction(async (transaction) => {
      const [tournamentSnapshot, registration, profileSnapshot] = await Promise.all([
        transaction.get(tournament),
        transaction.get(participant),
        transaction.get(profile),
      ]);
      if (!tournamentSnapshot.exists || tournamentSnapshot.get("status") !== "OPEN") {
        throw new HttpsError("not-found", "Tournament registration is not open.");
      }
      const data = tournamentSnapshot.data();
      if (now() >= data.registrationDeadlineMillis) {
        throw new HttpsError("deadline-exceeded", "Tournament registration has closed.");
      }
      if (!profileSnapshot.exists || profileSnapshot.get("accountStatus") !== "active") {
        throw new HttpsError("failed-precondition", "Create an active player profile first.");
      }
      if (registration.exists || data.participants.includes(uid)) {
        throw new HttpsError("already-exists", "You are already registered.");
      }
      if (data.participants.length >= data.maxParticipants) {
        throw new HttpsError("resource-exhausted", "This tournament is full.");
      }
      transaction.create(participant, {
        tournamentId,
        tournamentName: data.title,
        status: "REGISTERED",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      transaction.update(tournament, {
        participants: [...data.participants, uid],
        participantCount: data.participants.length + 1,
        updatedAt: FieldValue.serverTimestamp(),
      });
      transaction.set(notification, {
        type: "TOURNAMENT_REGISTRATION",
        actorUid: "system",
        data: { tournamentId, title: data.title },
        createdAt: FieldValue.serverTimestamp(),
        readAt: null,
      });
    });
    return { tournamentId, registered: true };
  }

  async function cancelTournamentRegistration(request) {
    validateData(request.data, ["tournamentId"]);
    const uid = requireUid(request);
    const tournamentId = validateId(request.data.tournamentId, "tournament ID");
    const tournament = tournamentRef(tournamentId);
    const participant = db.collection("tournamentParticipants").doc(uid)
      .collection("entries").doc(tournamentId);
    await db.runTransaction(async (transaction) => {
      const [tournamentSnapshot, registration] = await Promise.all([
        transaction.get(tournament),
        transaction.get(participant),
      ]);
      if (!tournamentSnapshot.exists || tournamentSnapshot.get("status") !== "OPEN"
        || now() >= tournamentSnapshot.get("registrationDeadlineMillis")) {
        throw new HttpsError("failed-precondition", "Registration can no longer be cancelled.");
      }
      if (!registration.exists) throw new HttpsError("not-found", "Registration was not found.");
      const remaining = tournamentSnapshot.get("participants").filter((id) => id !== uid);
      transaction.update(tournament, {
        participants: remaining,
        participantCount: remaining.length,
        updatedAt: FieldValue.serverTimestamp(),
      });
      transaction.delete(participant);
    });
    return { tournamentId, registered: false };
  }

  async function startTournament(request) {
    validateData(request.data, ["tournamentId"]);
    requireAdmin(request);
    const tournamentId = validateId(request.data.tournamentId, "tournament ID");
    const ref = tournamentRef(tournamentId);
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(ref);
      if (!snapshot.exists || snapshot.get("status") !== "OPEN") {
        throw new HttpsError("failed-precondition", "Tournament is not open.");
      }
      if (now() < snapshot.get("startsAtMillis")) {
        throw new HttpsError("failed-precondition", "Tournament start time has not arrived.");
      }
      if (snapshot.get("participants").length < 2) {
        throw new HttpsError("failed-precondition", "At least two players must register.");
      }
      const participantUids = snapshot.get("participants");
      const statsRefs = participantUids.map((uid) => db.collection("competitiveProfiles").doc(uid));
      const statsDocs = await Promise.all(statsRefs.map((statsRef) => transaction.get(statsRef)));
      const bracket = createTournamentBracket(participantUids.map((uid, index) => ({
        uid,
        rating: statsDocs[index].exists ? statsDocs[index].get("rating") : INITIAL_RATING,
      })));
      // Initial byes are resolved by the deterministic bracket builder; subsequent matches
      // are activated only after an authoritative multiplayer result is committed.
      transaction.update(ref, {
        status: "LIVE",
        bracket: serializeBracket(bracket),
        startedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      for (const uid of participantUids) {
        transaction.set(db.collection("tournamentParticipants").doc(uid)
          .collection("entries").doc(tournamentId), {
          tournamentId,
          tournamentName: snapshot.get("title"),
          status: "LIVE",
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
        transaction.set(notificationRef(uid, `tournament_started_${tournamentId}`), {
          type: "TOURNAMENT_STARTED",
          actorUid: "system",
          data: { tournamentId, title: snapshot.get("title") },
          createdAt: FieldValue.serverTimestamp(),
          readAt: null,
        });
      }
      for (const match of bracket.rounds.flat().filter((item) =>
        item.status === "PENDING" && item.players.length === 2 && item.players.every(Boolean))) {
        for (const uid of match.players) {
          transaction.set(notificationRef(
            uid,
            `tournament_match_assigned_${tournamentId}_${match.matchId}`
          ), {
            type: "TOURNAMENT_MATCH_ASSIGNED",
            actorUid: "system",
            data: {
              tournamentId,
              tournamentMatchId: match.matchId,
              round: match.round,
            },
            createdAt: FieldValue.serverTimestamp(),
            readAt: null,
          });
        }
      }
    });
    return { tournamentId, status: "LIVE" };
  }

  async function getTournament(request) {
    validateData(request.data, ["tournamentId"]);
    const uid = requireUid(request);
    const tournamentId = validateId(request.data.tournamentId, "tournament ID");
    const snapshot = await tournamentRef(tournamentId).get();
    if (!snapshot.exists) throw new HttpsError("not-found", "Tournament was not found.");
    const data = snapshot.data();
    const bracketState = data.bracket ? deserializeBracket(data.bracket) : null;
    const uids = [...new Set(bracketState?.rounds?.flat(2)
      .flatMap((match) => match.players || []).filter(Boolean)
      || data.participants)];
    const profiles = await db.getAll(...uids.map((uid) => db.collection("publicPlayers").doc(uid)));
    const names = new Map(uids.map((uid, index) => [
      uid,
      profiles[index].exists && profiles[index].get("profileVisibility") === "public"
        ? profiles[index].get("displayName")
        : "Private player",
    ]));
    const bracket = bracketState ? {
      format: data.bracket.format,
      championUid: data.bracket.championUid,
      rounds: bracketState.rounds.map((round) => round.map((match) => ({
        matchId: match.matchId,
        round: match.round,
        players: match.players.map((playerUid) => playerUid
          ? { uid: playerUid, displayName: names.get(playerUid) || "Private player" }
          : null),
        status: match.status,
        winnerUid: match.winnerUid,
        isBye: match.isBye,
        onlineMatchId: match.onlineMatchId,
        canStart: data.status === "LIVE"
          && match.status === "PENDING"
          && match.players.length === 2
          && match.players.includes(uid),
      }))),
    } : null;
    return {
      tournamentId,
      title: data.title,
      description: data.description,
      format: data.format,
      status: data.status,
      participantCount: data.participantCount,
      maxParticipants: data.maxParticipants,
      registrationDeadlineMillis: data.registrationDeadlineMillis,
      startsAtMillis: data.startsAtMillis,
      rewards: data.rewards,
      bracket,
      championUid: data.championUid,
    };
  }

  async function prepareMatchCompletion(transaction, state, rewardPlans = []) {
    if (state.status !== "COMPLETED" || state.result?.verified !== true || !state.winnerUid) {
      return null;
    }
    const matchRecordRef = db.collection("competitiveMatchRecords").doc(state.matchId);
    if ((await transaction.get(matchRecordRef)).exists) return null;
    const positions = state.result.positions;
    if (!Array.isArray(positions) || positions.length !== state.playerUids.length
      || new Set(positions.map((entry) => entry.uid)).size !== state.playerUids.length
      || state.playerUids.some((uid) => !positions.some((entry) => entry.uid === uid))
      || new Set(positions.map((entry) => entry.position)).size !== positions.length
      || positions.some((entry) => !Number.isSafeInteger(entry.position)
        || entry.position < 1 || entry.position > positions.length)
      || positions.find((entry) => entry.uid === state.winnerUid)?.position !== 1) {
      throw new HttpsError("failed-precondition", "The verified result placements are inconsistent.");
    }

    const profileRefs = state.playerUids.map((uid) => db.collection("competitiveProfiles").doc(uid));
    const publicRefs = state.playerUids.map((uid) => db.collection("publicPlayers").doc(uid));
    const seasonPointerRef = currentSeasonRef();
    const tournament = state.tournamentId ? tournamentRef(state.tournamentId) : null;
    const [profileDocs, publicDocs, seasonPointer, tournamentSnapshot] = await Promise.all([
      Promise.all(profileRefs.map((ref) => transaction.get(ref))),
      Promise.all(publicRefs.map((ref) => transaction.get(ref))),
      transaction.get(seasonPointerRef),
      tournament ? transaction.get(tournament) : Promise.resolve(null),
    ]);
    let seasonId = seasonPointer.exists ? seasonPointer.get("seasonId") : null;
    let seasonSnapshot = seasonId
      ? await transaction.get(seasonRef(seasonId))
      : null;
    const completedAtMillis = state.result.completedAtMillis;
    const seasonActive = seasonSnapshot?.exists
      && seasonSnapshot.get("status") === "ACTIVE"
      && completedAtMillis >= seasonSnapshot.get("startsAtMillis")
      && completedAtMillis < seasonSnapshot.get("endsAtMillis");
    if (!seasonActive) {
      seasonId = null;
      seasonSnapshot = null;
    }
    const oldStats = profileDocs.map(statsFrom);
    const placements = positions.map((entry) => ({
      uid: entry.uid,
      position: entry.position,
      rating: oldStats[state.playerUids.indexOf(entry.uid)].rating,
    }));
    const deltas = calculateRatingDeltas(placements);
    const nextStats = oldStats.map((stats, index) => {
      const uid = state.playerUids[index];
      const won = uid === state.winnerUid;
      const delta = deltas[uid];
      const rating = Math.max(0, stats.rating + delta);
      const matches = stats.verifiedMatches + 1;
      const wins = stats.wins + (won ? 1 : 0);
      const losses = stats.losses + (won ? 0 : 1);
      const winStreak = won ? stats.winStreak + 1 : 0;
      return {
        ...stats,
        rating,
        verifiedMatches: matches,
        wins,
        losses,
        winPercentage: winPercentage(wins, matches),
        winRateEligible: matches >= MIN_WIN_RATE_MATCHES,
        bestRating: Math.max(stats.bestRating, rating),
        bestRank: stats.bestRank == null
          ? positions.find((entry) => entry.uid === uid).position
          : Math.min(stats.bestRank, positions.find((entry) => entry.uid === uid).position),
        winStreak,
        bestWinStreak: Math.max(stats.bestWinStreak, winStreak),
        isPublic: publicDocs[index].exists
          && publicDocs[index].get("profileVisibility") === "public",
      };
    });

    let seasonPlayers = null;
    let seasonEntries = null;
    if (seasonId) {
      const seasonRefs = state.playerUids.map((uid) => seasonStatsRef(seasonId, uid));
      const seasonDocs = await Promise.all(seasonRefs.map((ref) => transaction.get(ref)));
      seasonPlayers = seasonDocs.map((snapshot, index) => {
        const old = snapshot.exists ? snapshot.data() : {
          seasonRating: INITIAL_RATING,
          seasonPoints: 0,
          verifiedMatches: 0,
          wins: 0,
          losses: 0,
        };
        const uid = state.playerUids[index];
        const won = uid === state.winnerUid;
        const delta = calculateRatingDeltas(state.playerUids.map((playerUid, playerIndex) => ({
          uid: playerUid,
          rating: seasonDocs[playerIndex].exists
            ? seasonDocs[playerIndex].get("seasonRating")
            : INITIAL_RATING,
          position: positions.find((entry) => entry.uid === playerUid).position,
        })))[uid];
        const position = positions.find((entry) => entry.uid === uid).position;
        const matches = old.verifiedMatches + 1;
        const wins = old.wins + (won ? 1 : 0);
        return {
          uid,
          seasonRating: Math.max(0, old.seasonRating + delta),
          seasonPoints: old.seasonPoints + Math.max(0, state.playerUids.length - position),
          verifiedMatches: matches,
          wins,
          losses: old.losses + (won ? 0 : 1),
          winPercentage: winPercentage(wins, matches),
          isPublic: nextStats[index].isPublic,
        };
      });
      seasonEntries = seasonPlayers.map((player) => seasonEntryRef(seasonId, player.uid));
    }

    let tournamentUpdate = null;
    if (state.tournamentId) {
      if (!tournamentSnapshot?.exists || tournamentSnapshot.get("status") !== "LIVE"
        || !tournamentSnapshot.get("bracket")) {
        throw new HttpsError("failed-precondition", "The tournament is no longer active.");
      }
      const currentBracket = deserializeBracket(tournamentSnapshot.get("bracket"));
      const bracketMatch = currentBracket.rounds.flat()
        .find((match) => match.matchId === state.tournamentMatchId);
      if (!bracketMatch || bracketMatch.status !== "ACTIVE"
        || bracketMatch.onlineMatchId !== state.matchId
        || bracketMatch.players.some((uid) => !state.playerUids.includes(uid))) {
        throw new HttpsError("failed-precondition", "Match does not belong to the active tournament pairing.");
      }
      let bracket;
      try {
        bracket = advanceBracket(
          currentBracket,
          state.tournamentMatchId,
          state.winnerUid,
          state.matchId
        );
      } catch {
        throw new HttpsError("failed-precondition", "Tournament bracket progression is invalid.");
      }
      tournamentUpdate = {
        ref: tournament,
        snapshot: tournamentSnapshot,
        bracket,
        isComplete: bracket.isComplete === true,
      };
    }

    const unlocks = [];
    for (let index = 0; index < state.playerUids.length; index += 1) {
      const old = oldStats[index];
      const next = nextStats[index];
      for (const definition of COMPETITIVE_ACHIEVEMENTS) {
        if (definition.condition(old, next)) {
          const ref = db.collection("competitiveAchievements").doc(state.playerUids[index])
            .collection("unlocks").doc(definition.id);
          const existing = await transaction.get(ref);
          if (!existing.exists) {
            unlocks.push({ ref, uid: state.playerUids[index], definition });
            const reward = rewardPlans.find((plan) => plan.uid === state.playerUids[index]);
            if (reward) {
              reward.xpAward += definition.xp;
              reward.newXp += definition.xp;
              reward.newLevel = Math.floor(reward.newXp / 1_000) + 1;
            }
          }
        }
      }
    }
    if (tournamentUpdate?.isComplete) {
      const championUid = tournamentUpdate.bracket.championUid;
      const championReward = rewardPlans.find((plan) => plan.uid === championUid);
      const configured = tournamentUpdate.snapshot.get("rewards")?.champion || {};
      if (championReward) {
        const bonusCoins = configured.coins || 0;
        const bonusXp = configured.xp || 0;
        if (!Number.isSafeInteger(championReward.newBalance + bonusCoins)
          || !Number.isSafeInteger(championReward.totalCoinsEarned + bonusCoins)
          || !Number.isSafeInteger(championReward.newXp + bonusXp)) {
          throw new HttpsError(
            "resource-exhausted",
            "Tournament rewards exceed supported account limits."
          );
        }
        championReward.coinAward += bonusCoins;
        championReward.newBalance += bonusCoins;
        championReward.totalCoinsEarned += bonusCoins;
        championReward.xpAward += bonusXp;
        championReward.newXp += bonusXp;
        championReward.newLevel = Math.floor(championReward.newXp / 1_000) + 1;
      }
    }

    return {
      matchId: state.matchId,
      uids: state.playerUids,
      placements,
      deltas,
      oldStats,
      nextStats,
      seasonId,
      seasonPlayers,
      seasonEntries,
      unlocks,
      tournamentUpdate,
    };
  }

  function writeMatchCompletion(transaction, prepared) {
    if (!prepared) return;
    const timestamp = FieldValue.serverTimestamp();
    transaction.create(db.collection("competitiveMatchRecords").doc(prepared.matchId), {
      matchId: prepared.matchId,
      verified: true,
      placements: prepared.placements.map((placement) => ({
        ...placement,
        ratingDelta: prepared.deltas[placement.uid],
      })),
      createdAt: timestamp,
    });
    prepared.uids.forEach((uid, index) => {
      const stats = prepared.nextStats[index];
      transaction.set(db.collection("competitiveProfiles").doc(uid), {
        ...stats,
        updatedAt: timestamp,
      }, { merge: true });
      transaction.create(db.collection("competitiveRatingHistory").doc(uid)
        .collection("matches").doc(prepared.matchId), {
        matchId: prepared.matchId,
        previousRating: prepared.oldStats[index].rating,
        rating: stats.rating,
        delta: prepared.deltas[uid],
        position: prepared.placements.find((placement) => placement.uid === uid).position,
        createdAt: timestamp,
      });
    });
    if (prepared.seasonId) {
      prepared.seasonPlayers.forEach((player, index) => {
        transaction.set(seasonStatsRef(prepared.seasonId, player.uid), {
          ...player,
          updatedAt: timestamp,
        }, { merge: true });
        transaction.set(prepared.seasonEntries[index], {
          seasonId: prepared.seasonId,
          ...player,
          updatedAt: timestamp,
        }, { merge: true });
      });
    }
    for (const unlock of prepared.unlocks) {
      transaction.create(unlock.ref, {
        title: unlock.definition.title,
        xpReward: unlock.definition.xp,
        matchId: prepared.matchId,
        unlockedAt: timestamp,
      });
      transaction.create(notificationRef(
        unlock.uid,
        `competitive_achievement_${unlock.definition.id}`
      ), {
        type: "COMPETITIVE_ACHIEVEMENT",
        actorUid: "system",
        data: { achievementId: unlock.definition.id, title: unlock.definition.title },
        createdAt: timestamp,
        readAt: null,
      });
    }
    if (prepared.tournamentUpdate) {
      const { ref, snapshot, bracket, isComplete } = prepared.tournamentUpdate;
      const completedMatch = bracket.rounds.flat().find((match) =>
        match.onlineMatchId === prepared.matchId);
      transaction.update(ref, {
        bracket: serializeBracket(bracket),
        status: isComplete ? "COMPLETED" : "LIVE",
        championUid: bracket.championUid,
        completedAt: isComplete ? timestamp : null,
        updatedAt: timestamp,
      });
      if (completedMatch) {
        for (const uid of completedMatch.players) {
          transaction.set(notificationRef(uid, `tournament_match_${prepared.matchId}`), {
            type: "TOURNAMENT_MATCH_COMPLETED",
            actorUid: "system",
            data: {
              tournamentId: snapshot.id,
              title: snapshot.get("title"),
              round: completedMatch.round,
              winnerUid: completedMatch.winnerUid,
            },
            createdAt: timestamp,
            readAt: null,
          });
        }
      }
      if (!isComplete) {
        const nextMatch = bracket.rounds.flat().find((match) =>
          match.status === "PENDING" && match.players.length === 2
          && match.players.every(Boolean)
          && match.round > (completedMatch?.round ?? 0));
        if (nextMatch) {
          for (const uid of nextMatch.players) {
            transaction.set(notificationRef(
              uid,
              `tournament_match_assigned_${snapshot.id}_${nextMatch.matchId}`
            ), {
              type: "TOURNAMENT_MATCH_ASSIGNED",
              actorUid: "system",
              data: {
                tournamentId: snapshot.id,
                tournamentMatchId: nextMatch.matchId,
                round: nextMatch.round,
              },
              createdAt: timestamp,
              readAt: null,
            });
          }
        }
      }
      if (isComplete) {
        const participants = snapshot.get("participants");
        participants.forEach((uid) => {
          const elimination = bracket.rounds.flat().find((match) =>
            match.status === "COMPLETED"
            && match.players.includes(uid)
            && match.winnerUid !== uid);
          const place = uid === bracket.championUid
            ? 1
            : elimination ? (2 ** (bracket.rounds.length - elimination.round)) + 1
              : null;
          transaction.set(db.collection("tournamentParticipants").doc(uid)
            .collection("entries").doc(snapshot.id), {
            status: "COMPLETED",
            place,
            updatedAt: timestamp,
          }, { merge: true });
        });
        for (const uid of participants) {
          transaction.set(notificationRef(uid, `tournament_completed_${snapshot.id}`), {
            type: "TOURNAMENT_COMPLETED",
            actorUid: "system",
            data: {
              tournamentId: snapshot.id,
              title: snapshot.get("title"),
              championUid: bracket.championUid,
            },
            createdAt: timestamp,
            readAt: null,
          });
        }
      }
    }
  }

  async function getCurrentSeason(request) {
    validateData(request.data, []);
    requireUid(request);
    const season = await getActiveSeason();
    return season ? {
      seasonId: season.id,
      title: season.title,
      startsAtMillis: season.startsAtMillis,
      endsAtMillis: season.endsAtMillis,
      rewards: season.rewards,
      serverTimeMillis: now(),
    } : { season: null, serverTimeMillis: now() };
  }

  return {
    activateSeason,
    cancelTournamentRegistration,
    createSeason,
    createTournament,
    getCompetitiveLeaderboard,
    getCurrentSeason,
    getFriendsLeaderboard,
    getMyCompetitiveProfile,
    finalizeDueSeasons,
    finalizeSeason,
    listSeasonHistory,
    getTournament,
    listTournaments,
    prepareMatchCompletion,
    registerTournament,
    startTournament,
    writeMatchCompletion,
  };
}

function validateRewards(rewards) {
  if (!Array.isArray(rewards) || rewards.length > 10
    || rewards.some((reward, index) =>
      !Number.isSafeInteger(reward.place) || reward.place !== index + 1
      || !Number.isSafeInteger(reward.coins) || reward.coins < 0 || reward.coins > 10_000
      || !Number.isSafeInteger(reward.xp) || reward.xp < 0 || reward.xp > 10_000)) {
    throw new HttpsError("invalid-argument", "Season rewards must be ordered safe non-negative values.");
  }
  return rewards;
}

function validateTournamentRewards(rewards) {
  if (!rewards || typeof rewards !== "object" || Array.isArray(rewards)) {
    throw new HttpsError("invalid-argument", "Tournament reward configuration is invalid.");
  }
  const champion = rewards.champion || { coins: 0, xp: 0 };
  if (!Number.isSafeInteger(champion.coins) || champion.coins < 0 || champion.coins > 10_000
    || !Number.isSafeInteger(champion.xp) || champion.xp < 0 || champion.xp > 10_000) {
    throw new HttpsError("invalid-argument", "Tournament rewards are outside supported limits.");
  }
  return { champion: { coins: champion.coins, xp: champion.xp } };
}

function timestampMillis(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return null;
}

module.exports = {
  DEFAULT_SEASON_REWARDS,
  createCompetitiveService,
};
