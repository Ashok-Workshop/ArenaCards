const { randomBytes, randomUUID } = require("node:crypto");
const { HttpsError } = require("firebase-functions/v2/https");
const {
  MultiplayerError,
  applyAction,
  createGame,
  expireTurn,
  publicMatch,
} = require("./multiplayer-engine");
const { FieldPath, FieldValue } = require("./firestore-client");
const { deserializeBracket, serializeBracket } = require("./competitive-domain");

const ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const ROOM_CODE_LENGTH = 10;
const ROOM_TTL_MILLIS = 24 * 60 * 60 * 1000;
const ROOM_CODE_WINDOW_MILLIS = 60 * 1000;
const ROOM_CODE_MAX_ATTEMPTS = 10;
const MAX_HISTORY_PAGE_SIZE = 50;

function createMultiplayerService(db, options = {}) {
  const now = options.now || Date.now;
  const codeGenerator = options.codeGenerator || generateRoomCode;
  const prepareCompetitiveCompletion = options.prepareCompetitiveCompletion;
  const writeCompetitiveCompletion = options.writeCompetitiveCompletion;

  function uidFrom(request) {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in to play online.");
    if (
      request.auth.token.firebase?.sign_in_provider === "password"
      && request.auth.token.email_verified !== true
    ) {
      throw new HttpsError("permission-denied", "Verify your email before playing online.");
    }
    return uid;
  }

  function validateData(data, keys) {
    if (!data || typeof data !== "object" || Array.isArray(data)) {
      throw new HttpsError("invalid-argument", "Request data must be an object.");
    }
    if (Object.keys(data).some((key) => !keys.includes(key))) {
      throw new HttpsError("invalid-argument", "Request contains an unsupported field.");
    }
  }

  function validateRequestId(value) {
    if (typeof value !== "string" || !/^[A-Za-z0-9_-]{8,80}$/.test(value)) {
      throw new HttpsError("invalid-argument", "A valid request ID is required.");
    }
    return value;
  }

  function validateMatchId(value) {
    if (typeof value !== "string" || !/^[A-Za-z0-9_-]{8,80}$/.test(value)) {
      throw new HttpsError("invalid-argument", "A valid match ID is required.");
    }
    return value;
  }

  function profileRef(uid) {
    return db.collection("users").doc(uid);
  }

  function activeRef(uid) {
    return db.collection("onlinePlayers").doc(uid);
  }

  function queueRef(uid) {
    return db.collection("matchmakingQueue").doc(uid);
  }

  function operationRef(uid, requestId) {
    return db.collection("multiplayerRequests").doc(uid)
      .collection("operations").doc(requestId);
  }

  function matchRef(matchId) {
    return db.collection("onlineMatches").doc(matchId);
  }

  function serverStateRef(matchId) {
    return matchRef(matchId).collection("private").doc("state");
  }

  function handRef(matchId, uid) {
    return matchRef(matchId).collection("hands").doc(uid);
  }

  function publicPlayer(profile, uid) {
    if (!profile || profile.accountStatus !== "active") {
      throw new HttpsError("failed-precondition", "Create an active player profile before playing online.");
    }
    return {
      uid,
      displayName: profile.displayName,
      username: profile.username,
      gamerId: profile.gamerId,
      avatarId: profile.avatarId,
    };
  }

  function mapError(error) {
    if (error instanceof MultiplayerError) {
      throw new HttpsError(error.code, error.message);
    }
    throw error;
  }

  function stateForStorage(state) {
    return {
      ...state,
      updatedAt: FieldValue.serverTimestamp(),
    };
  }

  function handForStorage(state, uid) {
    return {
      cards: state.hands[uid],
      stateVersion: state.stateVersion,
      drawnCardId: state.currentPlayerIndex >= 0
        && state.playerUids[state.currentPlayerIndex] === uid
        ? state.drawnCardId
        : null,
      updatedAt: FieldValue.serverTimestamp(),
    };
  }

  function writeNewMatch(transaction, state, timestamp) {
    const match = matchRef(state.matchId);
    transaction.create(match, {
      ...publicMatch(state),
      createdAt: timestamp,
      updatedAt: timestamp,
      completedAt: null,
    });
    transaction.create(match.collection("private").doc("state"), stateForStorage(state));
    for (const uid of state.playerUids) {
      transaction.create(match.collection("hands").doc(uid), handForStorage(state, uid));
      transaction.set(activeRef(uid), {
        status: "IN_MATCH",
        activeMatchId: state.matchId,
        lastSeenAtMillis: state.players.find((player) => player.uid === uid).lastSeenAtMillis,
        updatedAt: timestamp,
      });
    }
  }

  function writeUpdatedMatch(transaction, state, timestamp, nowMillis = now()) {
    const match = matchRef(state.matchId);
    transaction.set(match, {
      ...publicMatch(state, nowMillis),
      updatedAt: timestamp,
      ...(state.result ? { completedAt: timestamp } : {}),
    }, { merge: true });
    transaction.set(serverStateRef(state.matchId), stateForStorage(state));
    for (const uid of state.playerUids) {
      transaction.set(handRef(state.matchId, uid), handForStorage(state, uid));
    }
  }

  function readPlayerState(transaction, uids) {
    return Promise.all(uids.map((uid) => transaction.get(activeRef(uid))));
  }

  function assertAvailable(snapshot, uid) {
    if (
      snapshot.exists
      && ["QUEUED", "IN_ROOM", "IN_MATCH"].includes(snapshot.get("status"))
    ) {
      throw new HttpsError("failed-precondition", "Leave your current queue, room, or match first.");
    }
    return uid;
  }

  function buildHistoryRecord(state, uid) {
    const player = state.players.find((item) => item.uid === uid);
    const position = state.result?.positions.find((item) => item.uid === uid);
    return {
      matchId: state.matchId,
      status: state.status,
      verified: state.result?.verified === true,
      outcome: state.winnerUid == null
        ? "NO_WINNER"
        : state.winnerUid === uid ? "WIN" : "LOSS",
      winnerUid: state.winnerUid,
      finalPosition: position?.position ?? null,
      durationMillis: state.result?.durationMillis ?? 0,
      completionReason: state.completionReason,
      completedAt: new Date(state.result?.completedAtMillis ?? now()),
      players: state.players.map(({ uid: playerUid, displayName, username, gamerId, avatarId, seat }) => ({
        uid: playerUid,
        displayName,
        username,
        gamerId,
        avatarId,
        seat,
      })),
      playerDisplayName: player.displayName,
    };
  }

  async function prepareRewards(transaction, state) {
    const uids = state.playerUids;
    const refs = uids.flatMap((uid) => [
      db.collection("wallets").doc(uid),
      db.collection("progression").doc(uid),
      profileRef(uid),
      db.collection("publicPlayers").doc(uid),
    ]);
    const snapshots = await Promise.all(refs.map((ref) => transaction.get(ref)));
    const plans = [];
    for (let index = 0; index < uids.length; index += 1) {
      const uid = uids[index];
      const [walletSnapshot, progressionSnapshot, userSnapshot, publicSnapshot] =
        snapshots.slice(index * 4, index * 4 + 4);
      const wallet = walletSnapshot.exists ? walletSnapshot.data() : {
        coinsBalance: 0,
        totalCoinsEarned: 0,
        totalCoinsSpent: 0,
        createdAt: FieldValue.serverTimestamp(),
      };
      if (
        !Number.isSafeInteger(wallet.coinsBalance)
        || wallet.coinsBalance < 0
        || !Number.isSafeInteger(wallet.totalCoinsEarned)
        || wallet.totalCoinsEarned < 0
        || !Number.isSafeInteger(wallet.totalCoinsSpent)
        || wallet.totalCoinsSpent < 0
        || wallet.totalCoinsEarned - wallet.totalCoinsSpent !== wallet.coinsBalance
      ) {
        throw new HttpsError("internal", "Wallet integrity check failed; match rewards were not applied.");
      }
      const profile = userSnapshot.exists ? userSnapshot.data() : null;
      const progression = progressionSnapshot.exists ? progressionSnapshot.data() : {};
      const xp = progressionSnapshot.exists
        ? progression.experiencePoints
        : (profile?.experiencePoints ?? 0);
      if (!Number.isSafeInteger(xp) || xp < 0) {
        throw new HttpsError("internal", "Progression integrity check failed.");
      }
      const isWinner = uid === state.winnerUid;
      const coinAward = 25 + (isWinner ? 75 : 0);
      const xpAward = 50 + (isWinner ? 50 : 0);
      const newBalance = wallet.coinsBalance + coinAward;
      const totalCoinsEarned = wallet.totalCoinsEarned + coinAward;
      const newXp = xp + xpAward;
      if (
        !Number.isSafeInteger(newBalance)
        || !Number.isSafeInteger(totalCoinsEarned)
        || !Number.isSafeInteger(newXp)
      ) {
        throw new HttpsError("resource-exhausted", "Reward balance is at its supported limit.");
      }
      plans.push({
        uid,
        walletSnapshot,
        progressionSnapshot,
        userSnapshot,
        publicSnapshot,
        wallet,
        xp,
        newBalance,
        totalCoinsEarned,
        newXp,
        newLevel: Math.floor(newXp / 1_000) + 1,
        coinAward,
        xpAward,
        isWinner,
      });
    }
    return plans;
  }

  function writeVerifiedResult(transaction, state, timestamp, rewardPlans) {
    for (const player of state.players) {
      const historyRef = db.collection("onlineMatchHistory").doc(player.uid)
        .collection("matches").doc(state.matchId);
      transaction.create(historyRef, buildHistoryRecord(state, player.uid));
    }
    if (state.status !== "COMPLETED" || !state.winnerUid) return;

    for (const plan of rewardPlans) {
      const uid = plan.uid;
      const walletRef = db.collection("wallets").doc(uid);
      const progressionRef = db.collection("progression").doc(uid);
      const transactionId = `match_${state.matchId}`;
      transaction.set(walletRef, {
        coinsBalance: plan.newBalance,
        totalCoinsEarned: plan.totalCoinsEarned,
        totalCoinsSpent: plan.wallet.totalCoinsSpent,
        createdAt: plan.wallet.createdAt,
        updatedAt: timestamp,
      });
      transaction.create(walletRef.collection("transactions").doc(transactionId), {
        transactionId,
        uid,
        type: "VERIFIED_MATCH_REWARD",
        amount: plan.coinAward,
        balanceBefore: plan.wallet.coinsBalance,
        balanceAfter: plan.newBalance,
        source: "verified_online_match",
        timestamp,
        idempotencyKey: state.matchId,
        referenceId: state.matchId,
        status: "COMMITTED",
      });
      transaction.set(progressionRef, {
        experiencePoints: plan.newXp,
        level: plan.newLevel,
        totalXpEarned: (plan.progressionSnapshot.get("totalXpEarned") || plan.xp)
          + plan.xpAward,
        updatedAt: timestamp,
        ...(plan.progressionSnapshot.exists ? {} : { createdAt: timestamp }),
      }, { merge: true });
      transaction.create(progressionRef.collection("experienceEvents").doc(transactionId), {
        eventId: transactionId,
        type: "VERIFIED_MATCH",
        amount: plan.xpAward,
        previousExperiencePoints: plan.xp,
        newExperiencePoints: plan.newXp,
        source: "verified_online_match",
        referenceId: state.matchId,
        timestamp,
        status: "COMMITTED",
      });

      if (plan.userSnapshot.exists) {
        const user = plan.userSnapshot.data();
        transaction.set(plan.userSnapshot.ref, {
          totalMatches: (user.totalMatches || 0) + 1,
          wins: (user.wins || 0) + (plan.isWinner ? 1 : 0),
          losses: (user.losses || 0) + (plan.isWinner ? 0 : 1),
          experiencePoints: plan.newXp,
          playerLevel: plan.newLevel,
          updatedAt: timestamp,
        }, { merge: true });
      }
      if (plan.publicSnapshot.exists) {
        const publicData = plan.publicSnapshot.data();
        transaction.set(plan.publicSnapshot.ref, {
          totalMatches: (publicData.totalMatches || 0) + 1,
          wins: (publicData.wins || 0) + (plan.isWinner ? 1 : 0),
          losses: (publicData.losses || 0) + (plan.isWinner ? 0 : 1),
          experiencePoints: plan.newXp,
          playerLevel: plan.newLevel,
          updatedAt: timestamp,
        }, { merge: true });
      }
    }
  }

  async function joinMatchmaking(request) {
    validateData(request.data, ["playerCount", "requestId"]);
    const uid = uidFrom(request);
    const count = request.data.playerCount;
    if (!Number.isInteger(count) || count < 2 || count > 4) {
      throw new HttpsError("invalid-argument", "Choose a two-, three-, or four-player match.");
    }
    const requestId = validateRequestId(request.data.requestId);
    const nowMillis = now();
    const timestamp = FieldValue.serverTimestamp();
    const ownQueueRef = queueRef(uid);
    const ownActiveRef = activeRef(uid);
    const ownProfileRef = profileRef(uid);
    const bucketRef = db.collection("matchmakingBuckets").doc(String(count));

    const outcome = await db.runTransaction(async (transaction) => {
    const [queueSnapshot, activeSnapshot, profileSnapshot, bucketSnapshot] = await Promise.all([
      transaction.get(ownQueueRef),
      transaction.get(ownActiveRef),
      transaction.get(ownProfileRef),
      transaction.get(bucketRef),
      ]);
      if (queueSnapshot.exists && queueSnapshot.get("status") === "MATCHED") {
        return { matched: true, matchId: queueSnapshot.get("matchId") };
      }
      if (
        queueSnapshot.exists
        && queueSnapshot.get("status") === "WAITING"
        && activeSnapshot.exists
        && activeSnapshot.get("status") === "QUEUED"
      ) {
        if (queueSnapshot.get("playerCount") !== count) {
          throw new HttpsError(
            "failed-precondition",
            "Cancel your current queue before choosing a different table size."
          );
        }
        if (queueSnapshot.get("expiresAtMillis") > nowMillis) {
          return {
            matched: false,
            waitingCount: 1,
            expiresAtMillis: queueSnapshot.get("expiresAtMillis"),
          };
        }
        throw new HttpsError("deadline-exceeded", "Your queue entry expired; cancel it before rejoining.");
      }
      assertAvailable(activeSnapshot, uid);
      const player = publicPlayer(profileSnapshot.data(), uid);

      const waitingQuery = db.collection("matchmakingQueue")
        .where("status", "==", "WAITING")
        .orderBy("createdAtMillis", "asc")
        .limit(40);
      const waiting = await transaction.get(waitingQuery);
      const waitingActive = await readPlayerState(transaction, waiting.docs.map((doc) => doc.id));
      const expiredIndexes = waiting.docs.map((doc, index) =>
        doc.get("expiresAtMillis") <= nowMillis ? index : -1
      ).filter((index) => index >= 0);
      const candidates = waiting.docs.map((doc, index) => ({ doc, active: waitingActive[index] }))
        .filter(({ doc, active }) =>
        doc.id !== uid
        && doc.get("playerCount") === count
        && nowMillis - doc.get("createdAtMillis") <= ROOM_TTL_MILLIS
        && (!active.exists || active.get("status") === "QUEUED")
        )
        .slice(0, count - 1)
        .map(({ doc }) => doc);
      for (const index of expiredIndexes) {
        const expiredEntry = waiting.docs[index];
        transaction.delete(expiredEntry.ref);
        const activeSnapshotForExpired = waitingActive[index];
        if (activeSnapshotForExpired.exists && activeSnapshotForExpired.get("status") === "QUEUED") {
          transaction.set(activeRef(expiredEntry.id), {
            status: "IDLE",
            activeMatchId: null,
            updatedAt: timestamp,
          }, { merge: true });
        }
      }
      const validEntries = candidates;
      if (validEntries.length < count - 1) {
        const expiresAtMillis = nowMillis + ROOM_TTL_MILLIS;
        transaction.set(bucketRef, {
          revision: (bucketSnapshot.get("revision") || 0) + 1,
          updatedAt: timestamp,
        }, { merge: true });
        transaction.set(ownQueueRef, {
          uid,
          player,
          playerCount: count,
          requestId,
          status: "WAITING",
          createdAtMillis: nowMillis,
          expiresAtMillis,
          updatedAt: timestamp,
        });
        transaction.set(ownActiveRef, {
          status: "QUEUED",
          activeMatchId: null,
          queueExpiresAtMillis: expiresAtMillis,
          updatedAt: timestamp,
        });
        return { matched: false, waitingCount: validEntries.length + 1 };
      }

      const matchPlayers = [player, ...validEntries.map((doc) => doc.get("player"))];
      const state = createGame(matchPlayers, { nowMillis, mode: "MATCHMAKING" });
      transaction.set(bucketRef, {
        revision: (bucketSnapshot.get("revision") || 0) + 1,
        updatedAt: timestamp,
      }, { merge: true });
      writeNewMatch(transaction, state, timestamp);
      transaction.set(ownQueueRef, {
        uid,
        player,
        playerCount: count,
        requestId,
        status: "MATCHED",
        matchId: state.matchId,
        createdAtMillis: queueSnapshot.get("createdAtMillis") || nowMillis,
        updatedAt: timestamp,
      });
      for (const entry of validEntries) {
        transaction.set(entry.ref, {
          status: "MATCHED",
          matchId: state.matchId,
          updatedAt: timestamp,
        }, { merge: true });
      }
      return { matched: true, matchId: state.matchId };
    });
    return { ...outcome, playerCount: count };
  }

  async function cancelMatchmaking(request) {
    validateData(request.data || {}, []);
    const uid = uidFrom(request);
    const timestamp = FieldValue.serverTimestamp();
    return db.runTransaction(async (transaction) => {
      const [queueSnapshot, activeSnapshot] = await Promise.all([
        transaction.get(queueRef(uid)),
        transaction.get(activeRef(uid)),
      ]);
      if (queueSnapshot.exists && queueSnapshot.get("status") === "MATCHED") {
        return { cancelled: false, matchId: queueSnapshot.get("matchId") || null };
      }
      if (queueSnapshot.exists) transaction.delete(queueRef(uid));
      if (activeSnapshot.exists && activeSnapshot.get("status") === "QUEUED") {
        transaction.set(activeRef(uid), {
          status: "IDLE",
          activeMatchId: null,
          updatedAt: timestamp,
        });
      }
      return { cancelled: true, matchId: null };
    });
  }

  async function createPrivateRoom(request) {
    validateData(request.data, [
      "playerCount",
      "requestId",
      "turnDurationMillis",
      "allowWildDrawFourChallenge",
      "mode",
    ]);
    const uid = uidFrom(request);
    const capacity = request.data.playerCount;
    if (!Number.isInteger(capacity) || capacity < 2 || capacity > 4) {
      throw new HttpsError("invalid-argument", "Room capacity must be two, three, or four.");
    }
    const turnDurationMillis = request.data.turnDurationMillis ?? 45_000;
    const allowWildDrawFourChallenge = request.data.allowWildDrawFourChallenge ?? true;
    const mode = request.data.mode ?? "STANDARD";
    if (![30_000, 45_000, 60_000].includes(turnDurationMillis)
      || typeof allowWildDrawFourChallenge !== "boolean"
      || mode !== "STANDARD") {
      throw new HttpsError("invalid-argument", "The selected private-room rules are unsupported.");
    }
    validateRequestId(request.data.requestId);
    const roomId = randomUUID();
    const codes = Array.from({ length: 3 }, () => codeGenerator());
    const timestamp = FieldValue.serverTimestamp();
    const nowMillis = now();
    const requestId = request.data.requestId;
    const createRequestRef = operationRef(uid, requestId);
    return db.runTransaction(async (transaction) => {
      const [requestSnapshot, profileSnapshot, activeSnapshot] = await Promise.all([
        transaction.get(createRequestRef),
        transaction.get(profileRef(uid)),
        transaction.get(activeRef(uid)),
      ]);
      if (requestSnapshot.exists) return requestSnapshot.get("response");
      assertAvailable(activeSnapshot, uid);
      const player = publicPlayer(profileSnapshot.data(), uid);
      const codeRefs = codes.map((code) => db.collection("roomCodeLookups").doc(code));
      const codeSnapshots = await Promise.all(codeRefs.map((ref) => transaction.get(ref)));
      const codeIndex = codeSnapshots.findIndex((snapshot) => !snapshot.exists);
      if (codeIndex < 0) {
        throw new HttpsError("resource-exhausted", "A private room code could not be allocated.");
      }
      const roomCode = codes[codeIndex];
      transaction.create(db.collection("privateRooms").doc(roomId), {
        roomId,
        roomCode,
        ownerUid: uid,
        memberUids: [uid],
        players: [{ ...player, ready: true }],
        capacity,
        status: "WAITING",
        mode,
        rules: {
          turnDurationMillis,
          allowWildDrawFourChallenge,
          drawStacking: false,
        },
        createdAtMillis: nowMillis,
        expiresAtMillis: nowMillis + ROOM_TTL_MILLIS,
        createdAt: timestamp,
        updatedAt: timestamp,
      });
      transaction.create(codeRefs[codeIndex], { roomId, createdAt: timestamp });
      transaction.set(activeRef(uid), {
        status: "IN_ROOM",
        roomId,
        activeMatchId: null,
        updatedAt: timestamp,
      });
      const response = {
        roomId,
        roomCode,
        capacity,
        expiresAtMillis: nowMillis + ROOM_TTL_MILLIS,
        mode,
        rules: {
          turnDurationMillis,
          allowWildDrawFourChallenge,
          drawStacking: false,
        },
      };
      transaction.create(createRequestRef, {
        type: "CREATE_PRIVATE_ROOM",
        response,
        createdAt: timestamp,
      });
      return response;
    });
  }

  async function joinPrivateRoom(request) {
    validateData(request.data, ["roomCode", "requestId"]);
    const uid = uidFrom(request);
    const roomCode = typeof request.data.roomCode === "string"
      ? request.data.roomCode.trim().toUpperCase()
      : "";
    if (!/^[A-HJ-NP-Z2-9]{10}$/.test(roomCode)) {
      throw new HttpsError("invalid-argument", "Enter a valid ten-character room code.");
    }
    validateRequestId(request.data.requestId);
    const nowMillis = now();
    const timestamp = FieldValue.serverTimestamp();
    const rateLimitRef = db.collection("roomJoinRateLimits").doc(uid);

    const outcome = await db.runTransaction(async (transaction) => {
      const [rateSnapshot, lookupSnapshot] = await Promise.all([
        transaction.get(rateLimitRef),
        transaction.get(db.collection("roomCodeLookups").doc(roomCode)),
      ]);
      const rate = rateSnapshot.exists ? rateSnapshot.data() : {};
      const inWindow = nowMillis - (rate.windowStartedAtMillis || 0) < ROOM_CODE_WINDOW_MILLIS;
      const attempts = inWindow ? rate.attempts || 0 : 0;
      if (attempts >= ROOM_CODE_MAX_ATTEMPTS) {
        return { error: "rate-limited" };
      }
      const nextRate = {
        windowStartedAtMillis: inWindow ? rate.windowStartedAtMillis : nowMillis,
        attempts: attempts + 1,
        updatedAt: timestamp,
      };
      if (!lookupSnapshot.exists) {
        transaction.set(rateLimitRef, nextRate);
        return { error: "not-found" };
      }

      const roomId = lookupSnapshot.get("roomId");
      const roomRef = db.collection("privateRooms").doc(roomId);
      const [roomSnapshot, activeSnapshot, profileSnapshot] = await Promise.all([
        transaction.get(roomRef),
        transaction.get(activeRef(uid)),
        transaction.get(profileRef(uid)),
      ]);
      if (!roomSnapshot.exists) return { error: "not-found" };
      const room = roomSnapshot.data();
      if (room.expiresAtMillis <= nowMillis || room.status !== "WAITING") {
        return { error: "expired" };
      }
      if (room.memberUids.includes(uid)) return { roomId, roomCode, joined: true };
      assertAvailable(activeSnapshot, uid);
      if (room.memberUids.length >= room.capacity) return { error: "full" };

      const blockers = room.memberUids.flatMap((memberUid) => [
        db.collection("userBlocks").doc(uid).collection("users").doc(memberUid),
        db.collection("userBlocks").doc(memberUid).collection("users").doc(uid),
      ]);
      const blockSnapshots = await Promise.all(blockers.map((ref) => transaction.get(ref)));
      transaction.set(rateLimitRef, nextRate);
      if (blockSnapshots.some((snapshot) => snapshot.exists)) return { error: "blocked" };
      const player = publicPlayer(profileSnapshot.data(), uid);
      transaction.set(roomRef, {
        memberUids: [...room.memberUids, uid],
        players: [...room.players, { ...player, ready: false }],
        updatedAt: timestamp,
      }, { merge: true });
      transaction.set(activeRef(uid), {
        status: "IN_ROOM",
        roomId,
        activeMatchId: null,
        updatedAt: timestamp,
      });
      return { roomId, roomCode, joined: true };
    });

    if (outcome.error === "rate-limited") {
      throw new HttpsError("resource-exhausted", "Too many room-code attempts. Try again later.");
    }
    if (outcome.error) {
      throw new HttpsError("not-found", "This room is unavailable or you cannot join it.");
    }
    return outcome;
  }

  async function setPrivateRoomReady(request) {
    validateData(request.data, ["roomId", "ready", "requestId"]);
    const uid = uidFrom(request);
    const roomId = validateMatchId(request.data.roomId);
    if (typeof request.data.ready !== "boolean") {
      throw new HttpsError("invalid-argument", "Ready state must be true or false.");
    }
    validateRequestId(request.data.requestId);
    const timestamp = FieldValue.serverTimestamp();
    return db.runTransaction(async (transaction) => {
      const ref = db.collection("privateRooms").doc(roomId);
      const roomSnapshot = await transaction.get(ref);
      if (!roomSnapshot.exists) throw new HttpsError("not-found", "This room no longer exists.");
      const room = roomSnapshot.data();
      if (!room.memberUids.includes(uid)) throw new HttpsError("permission-denied", "You are not in this room.");
      if (room.status !== "WAITING" || room.expiresAtMillis <= now()) {
        throw new HttpsError("failed-precondition", "This room is no longer available.");
      }
      const players = room.players.map((player) =>
        player.uid === uid ? { ...player, ready: request.data.ready } : player
      );
      transaction.set(ref, { players, updatedAt: timestamp }, { merge: true });
      return { roomId, ready: request.data.ready };
    });
  }

  async function leavePrivateRoom(request) {
    validateData(request.data, ["roomId", "requestId"]);
    const uid = uidFrom(request);
    const roomId = validateMatchId(request.data.roomId);
    validateRequestId(request.data.requestId);
    const timestamp = FieldValue.serverTimestamp();
    return db.runTransaction(async (transaction) => {
      const roomRef = db.collection("privateRooms").doc(roomId);
      const [roomSnapshot, activeSnapshot] = await Promise.all([
        transaction.get(roomRef),
        transaction.get(activeRef(uid)),
      ]);
      if (!roomSnapshot.exists) return { left: true, cancelled: false };
      const room = roomSnapshot.data();
      if (!room.memberUids.includes(uid)) return { left: true, cancelled: false };
      if (room.status !== "WAITING") {
        throw new HttpsError("failed-precondition", "A started match cannot be left from the room.");
      }
      if (room.ownerUid === uid) {
        const others = room.memberUids.filter((memberUid) => memberUid !== uid);
        const activeSnapshots = await readPlayerState(transaction, others);
        transaction.set(roomRef, { status: "CANCELLED", updatedAt: timestamp }, { merge: true });
        transaction.delete(db.collection("roomCodeLookups").doc(room.roomCode));
        transaction.set(activeRef(uid), { status: "IDLE", roomId: null, updatedAt: timestamp });
        others.forEach((memberUid, index) => {
          if (activeSnapshots[index].exists) {
            transaction.set(activeRef(memberUid), {
              status: "IDLE",
              roomId: null,
              updatedAt: timestamp,
            }, { merge: true });
          }
        });
        return { left: true, cancelled: true };
      }
      transaction.set(roomRef, {
        memberUids: room.memberUids.filter((memberUid) => memberUid !== uid),
        players: room.players.filter((player) => player.uid !== uid),
        updatedAt: timestamp,
      }, { merge: true });
      transaction.set(activeRef(uid), {
        status: "IDLE",
        roomId: null,
        updatedAt: timestamp,
      }, { merge: true });
      return { left: true, cancelled: false };
    });
  }

  async function startPrivateRoom(request) {
    validateData(request.data, ["roomId", "requestId"]);
    const uid = uidFrom(request);
    const roomId = validateMatchId(request.data.roomId);
    validateRequestId(request.data.requestId);
    const timestamp = FieldValue.serverTimestamp();
    const nowMillis = now();
    return db.runTransaction(async (transaction) => {
      const roomRef = db.collection("privateRooms").doc(roomId);
      const roomSnapshot = await transaction.get(roomRef);
      if (!roomSnapshot.exists) throw new HttpsError("not-found", "This room no longer exists.");
      const room = roomSnapshot.data();
      if (room.ownerUid !== uid) throw new HttpsError("permission-denied", "Only the room owner can start it.");
      if (room.status === "MATCHED" && room.matchId) {
        return { matchId: room.matchId, stateVersion: 1 };
      }
      if (room.status !== "WAITING" || room.expiresAtMillis <= nowMillis) {
        throw new HttpsError("failed-precondition", "This room is no longer available.");
      }
      if (room.memberUids.length < 2 || room.players.some((player) => !player.ready)) {
        throw new HttpsError("failed-precondition", "At least two players must be ready.");
      }
      const activeSnapshots = await readPlayerState(transaction, room.memberUids);
      room.memberUids.forEach((memberUid, index) => {
        if (
          !activeSnapshots[index].exists
          || activeSnapshots[index].get("status") !== "IN_ROOM"
          || activeSnapshots[index].get("roomId") !== roomId
        ) {
          throw new HttpsError("failed-precondition", "A room participant is no longer available.");
        }
      });
      const matchState = createGame(room.players, {
        nowMillis,
        mode: room.mode || "STANDARD",
        turnDurationMillis: room.rules?.turnDurationMillis,
        allowWildDrawFourChallenge: room.rules?.allowWildDrawFourChallenge,
      });
      writeNewMatch(transaction, matchState, timestamp);
      transaction.set(roomRef, {
        status: "MATCHED",
        matchId: matchState.matchId,
        updatedAt: timestamp,
      }, { merge: true });
      transaction.delete(db.collection("roomCodeLookups").doc(room.roomCode));
      return { matchId: matchState.matchId, stateVersion: matchState.stateVersion };
    });
  }

  async function startTournamentMatch(request) {
    validateData(request.data, ["tournamentId", "tournamentMatchId", "requestId"]);
    const uid = uidFrom(request);
    const tournamentId = validateMatchId(request.data.tournamentId);
    if (typeof request.data.tournamentMatchId !== "string"
      || !/^[A-Za-z0-9_-]{1,40}$/.test(request.data.tournamentMatchId)) {
      throw new HttpsError("invalid-argument", "A valid tournament match is required.");
    }
    const tournamentMatchId = request.data.tournamentMatchId;
    const requestId = validateRequestId(request.data.requestId);
    const operation = operationRef(uid, requestId);
    const tournamentRef = db.collection("tournaments").doc(tournamentId);
    const nowMillis = now();
    const timestamp = FieldValue.serverTimestamp();
    return db.runTransaction(async (transaction) => {
      const [oldOperation, tournamentSnapshot] = await Promise.all([
        transaction.get(operation),
        transaction.get(tournamentRef),
      ]);
      if (oldOperation.exists) return oldOperation.data().result;
      if (!tournamentSnapshot.exists || tournamentSnapshot.get("status") !== "LIVE") {
        throw new HttpsError("failed-precondition", "This tournament is not in progress.");
      }
      const tournament = tournamentSnapshot.data();
      const bracket = tournament.bracket ? deserializeBracket(tournament.bracket) : null;
      const bracketMatch = bracket?.rounds?.flat()
        .find((match) => match.matchId === tournamentMatchId);
      if (bracketMatch?.status === "ACTIVE"
        && bracketMatch.players.includes(uid)
        && bracketMatch.onlineMatchId) {
        const result = {
          tournamentId,
          tournamentMatchId,
          matchId: bracketMatch.onlineMatchId,
          stateVersion: 1,
        };
        transaction.create(operation, { requestId, result, createdAt: timestamp });
        return result;
      }
      if (!bracketMatch || bracketMatch.status !== "PENDING"
        || bracketMatch.players.length !== 2 || bracketMatch.players.some((playerUid) => !playerUid)
        || !bracketMatch.players.includes(uid)) {
        throw new HttpsError("failed-precondition", "You are not assigned to a playable tournament match.");
      }
      const [players, activePlayers, profiles] = await Promise.all([
        Promise.all(bracketMatch.players.map((playerUid) =>
          transaction.get(profileRef(playerUid)))),
        Promise.all(bracketMatch.players.map((playerUid) =>
          transaction.get(activeRef(playerUid)))),
        Promise.all(bracketMatch.players.map((playerUid) =>
          transaction.get(db.collection("users").doc(playerUid)))),
      ]);
      const [blockA, blockB] = await Promise.all([
        transaction.get(db.collection("userBlocks").doc(bracketMatch.players[0])
          .collection("users").doc(bracketMatch.players[1])),
        transaction.get(db.collection("userBlocks").doc(bracketMatch.players[1])
          .collection("users").doc(bracketMatch.players[0])),
      ]);
      if (blockA.exists || blockB.exists) {
        throw new HttpsError("failed-precondition", "A blocked pairing cannot start.");
      }
      bracketMatch.players.forEach((playerUid, index) => {
        if (!activePlayers[index].exists || activePlayers[index].get("status") !== "IDLE") {
          throw new HttpsError("failed-precondition", "A tournament player is currently unavailable.");
        }
        if (!profiles[index].exists || profiles[index].get("accountStatus") !== "active") {
          throw new HttpsError("failed-precondition", "A tournament player profile is not active.");
        }
      });
      if (activePlayers.some((snapshot) => snapshot.exists
        && ["QUEUED", "IN_ROOM", "IN_MATCH"].includes(snapshot.get("status")))) {
        throw new HttpsError("failed-precondition", "A tournament player is already active elsewhere.");
      }
      const matchId = randomUUID();
      const matchState = createGame(bracketMatch.players.map((playerUid, index) =>
        publicPlayer(profiles[index].data(), playerUid)), {
        nowMillis,
        mode: "TOURNAMENT",
        matchId,
        tournamentId,
        tournamentMatchId,
      });
      bracketMatch.status = "ACTIVE";
      bracketMatch.onlineMatchId = matchId;
      transaction.update(tournamentRef, {
        bracket: serializeBracket(bracket),
        updatedAt: timestamp,
      });
      writeNewMatch(transaction, matchState, timestamp);
      const result = { tournamentId, tournamentMatchId, matchId, stateVersion: 1 };
      transaction.create(operation, { requestId, result, createdAt: timestamp });
      return result;
    });
  }

  async function updatePrivateRoomRules(request) {
    validateData(request.data, [
      "roomId",
      "requestId",
      "turnDurationMillis",
      "allowWildDrawFourChallenge",
    ]);
    const uid = uidFrom(request);
    const roomId = validateMatchId(request.data.roomId);
    validateRequestId(request.data.requestId);
    const turnDurationMillis = request.data.turnDurationMillis;
    const allowWildDrawFourChallenge = request.data.allowWildDrawFourChallenge;
    if (![30_000, 45_000, 60_000].includes(turnDurationMillis)
      || typeof allowWildDrawFourChallenge !== "boolean") {
      throw new HttpsError("invalid-argument", "The selected private-room rules are unsupported.");
    }
    const roomRef = db.collection("privateRooms").doc(roomId);
    await db.runTransaction(async (transaction) => {
      const room = await transaction.get(roomRef);
      if (!room.exists) throw new HttpsError("not-found", "This private room is unavailable.");
      if (room.get("ownerUid") !== uid) {
        throw new HttpsError("permission-denied", "Only the room owner can change room rules.");
      }
      if (room.get("status") !== "WAITING" || room.get("expiresAtMillis") <= now()) {
        throw new HttpsError("failed-precondition", "Room rules are locked after the match starts.");
      }
      transaction.update(roomRef, {
        mode: "STANDARD",
        rules: {
          turnDurationMillis,
          allowWildDrawFourChallenge,
          drawStacking: false,
        },
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    return {
      roomId,
      mode: "STANDARD",
      rules: {
        turnDurationMillis,
        allowWildDrawFourChallenge,
        drawStacking: false,
      },
    };
  }

  async function getPrivateRoom(request) {
    validateData(request.data, ["roomId"]);
    const uid = uidFrom(request);
    const roomId = validateMatchId(request.data.roomId);
    const snapshot = await db.collection("privateRooms").doc(roomId).get();
    if (!snapshot.exists || !snapshot.get("memberUids").includes(uid)) {
      throw new HttpsError("not-found", "This room is unavailable.");
    }
    const room = snapshot.data();
    if (room.status === "WAITING" && room.expiresAtMillis <= now()) {
      throw new HttpsError("not-found", "This room has expired.");
    }
    return room;
  }

  async function observeOnlinePlayer(request) {
    validateData(request.data || {}, []);
    const uid = uidFrom(request);
    const snapshot = await activeRef(uid).get();
    if (!snapshot.exists) return { status: "IDLE", roomId: null, matchId: null };
    const state = snapshot.data();
    if (state.status === "IN_MATCH" && state.activeMatchId) {
      return { status: state.status, roomId: null, matchId: state.activeMatchId };
    }
    return {
      status: state.status,
      roomId: state.roomId || null,
      matchId: null,
      queueExpiresAtMillis: state.queueExpiresAtMillis || null,
    };
  }

  async function heartbeatOnlineMatch(request) {
    validateData(request.data, ["matchId"]);
    const uid = uidFrom(request);
    const matchId = validateMatchId(request.data.matchId);
    const timestamp = FieldValue.serverTimestamp();
    const nowMillis = now();
    return db.runTransaction(async (transaction) => {
      const [activeSnapshot, matchSnapshot, stateSnapshot] = await Promise.all([
        transaction.get(activeRef(uid)),
        transaction.get(matchRef(matchId)),
        transaction.get(serverStateRef(matchId)),
      ]);
      if (
        !matchSnapshot.exists
        || !matchSnapshot.get("playerUids").includes(uid)
        || !activeSnapshot.exists
        || activeSnapshot.get("activeMatchId") !== matchId
      ) {
        throw new HttpsError("not-found", "This active match is unavailable.");
      }
      if (!stateSnapshot.exists) throw new HttpsError("internal", "Match state is unavailable.");
      const state = stateSnapshot.data();
      const updated = {
        ...state,
        players: state.players.map((player) =>
          player.uid === uid
            ? { ...player, connectionState: "CONNECTED", lastSeenAtMillis: nowMillis }
            : player
        ),
        updatedAtMillis: nowMillis,
      };
      transaction.set(matchRef(matchId), {
        ...publicMatch(updated),
        updatedAt: timestamp,
      }, { merge: true });
      transaction.set(serverStateRef(matchId), stateForStorage(updated));
      transaction.set(activeRef(uid), {
        status: "IN_MATCH",
        activeMatchId: matchId,
        lastSeenAtMillis: nowMillis,
        updatedAt: timestamp,
      }, { merge: true });
      return { connected: true, serverTimeMillis: nowMillis };
    });
  }

  async function reconnectToMatch(request) {
    validateData(request.data, ["matchId"]);
    const uid = uidFrom(request);
    const matchId = validateMatchId(request.data.matchId);
    const [matchSnapshot, stateSnapshot] = await Promise.all([
      matchRef(matchId).get(),
      serverStateRef(matchId).get(),
    ]);
    if (!matchSnapshot.exists || !matchSnapshot.get("playerUids").includes(uid)) {
      throw new HttpsError("not-found", "This match is unavailable.");
    }
    if (!stateSnapshot.exists) throw new HttpsError("internal", "Match state is unavailable.");
    const state = stateSnapshot.data();
    return {
      match: publicMatch(state),
      hand: state.hands[uid],
      stateVersion: state.stateVersion,
    };
  }

  async function submitMatchAction(request) {
    validateData(request.data, [
      "matchId",
      "action",
      "cardId",
      "chosenColor",
      "expectedTurnNumber",
      "expectedStateVersion",
      "requestId",
    ]);
    const uid = uidFrom(request);
    const matchId = validateMatchId(request.data.matchId);
    const requestId = validateRequestId(request.data.requestId);
    if (
      !Number.isSafeInteger(request.data.expectedTurnNumber)
      || !Number.isSafeInteger(request.data.expectedStateVersion)
    ) {
      throw new HttpsError("invalid-argument", "A valid turn number and state version are required.");
    }
    if (!request.data.action || typeof request.data.action !== "string") {
      throw new HttpsError("invalid-argument", "A valid game action is required.");
    }
    const match = matchRef(matchId);
    const requestRef = match.collection("actions").doc(`${uid}_${requestId}`);
    const timestamp = FieldValue.serverTimestamp();
    const nowMillis = now();
    const committed = await db.runTransaction(async (transaction) => {
      const [oldRequest, matchSnapshot, stateSnapshot] = await Promise.all([
        transaction.get(requestRef),
        transaction.get(match),
        transaction.get(serverStateRef(matchId)),
      ]);
      if (oldRequest.exists) return { accepted: true, stateVersion: oldRequest.get("stateVersion") };
      if (!matchSnapshot.exists || !matchSnapshot.get("playerUids").includes(uid)) {
        throw new HttpsError("not-found", "This match is unavailable.");
      }
      if (!stateSnapshot.exists) throw new HttpsError("internal", "Match state is unavailable.");
      const state = stateSnapshot.data();
      if (request.data.expectedStateVersion !== state.stateVersion
        || request.data.expectedTurnNumber !== state.turnNumber) {
        throw new HttpsError("aborted", "The match changed. Refresh the table and try again.");
      }
      let updated;
      try {
        updated = applyAction(state, uid, {
          type: request.data.action,
          cardId: request.data.cardId,
          chosenColor: request.data.chosenColor,
        }, nowMillis);
      } catch (error) {
        mapError(error);
      }
      const rewardPlans = updated.status === "COMPLETED" && updated.winnerUid
        ? await prepareRewards(transaction, updated)
        : [];
      const competitiveUpdate = updated.result?.verified === true
        && prepareCompetitiveCompletion
        ? await prepareCompetitiveCompletion(transaction, updated, rewardPlans)
        : null;
      writeUpdatedMatch(transaction, updated, timestamp);
      transaction.create(requestRef, {
        uid,
        requestId,
        action: request.data.action,
        stateVersion: updated.stateVersion,
        createdAt: timestamp,
      });
      if (updated.result) {
        writeVerifiedResult(transaction, updated, timestamp, rewardPlans);
        if (writeCompetitiveCompletion) {
          writeCompetitiveCompletion(transaction, competitiveUpdate);
        }
        for (const playerUid of updated.playerUids) {
          transaction.set(activeRef(playerUid), {
            status: "IDLE",
            activeMatchId: null,
            lastMatchId: matchId,
            updatedAt: timestamp,
          }, { merge: true });
        }
      }
      return { accepted: true, stateVersion: updated.stateVersion, status: updated.status };
    });
    return committed;
  }

  async function processExpiredTurn(request) {
    validateData(request.data, ["matchId", "requestId"]);
    const uid = uidFrom(request);
    const matchId = validateMatchId(request.data.matchId);
    const requestId = validateRequestId(request.data.requestId);
    const match = matchRef(matchId);
    const requestRef = match.collection("timeouts").doc(requestId);
    const timestamp = FieldValue.serverTimestamp();
    const nowMillis = now();
    return db.runTransaction(async (transaction) => {
      const [oldRequest, matchSnapshot, stateSnapshot] = await Promise.all([
        transaction.get(requestRef),
        transaction.get(match),
        transaction.get(serverStateRef(matchId)),
      ]);
      if (oldRequest.exists) return { expired: oldRequest.get("expired") === true };
      if (!matchSnapshot.exists || !matchSnapshot.get("playerUids").includes(uid)) {
        throw new HttpsError("not-found", "This match is unavailable.");
      }
      if (!stateSnapshot.exists) throw new HttpsError("internal", "Match state is unavailable.");
      const updated = expireTurn(stateSnapshot.data(), nowMillis);
      if (!updated) {
        transaction.create(requestRef, {
          expired: false,
          stateVersion: stateSnapshot.get("stateVersion"),
          createdAt: timestamp,
        });
        return { expired: false };
      }
      writeUpdatedMatch(transaction, updated, timestamp);
      transaction.create(requestRef, {
        expired: true,
        stateVersion: updated.stateVersion,
        createdAt: timestamp,
      });
      if (updated.result) {
        writeVerifiedResult(transaction, updated, timestamp, []);
        for (const playerUid of updated.playerUids) {
          transaction.set(activeRef(playerUid), {
            status: "IDLE",
            activeMatchId: null,
            lastMatchId: matchId,
            updatedAt: timestamp,
          }, { merge: true });
        }
      }
      return { expired: true, status: updated.status, stateVersion: updated.stateVersion };
    });
  }

  async function processScheduledTimeout(matchId, workerRunId) {
    const match = matchRef(matchId);
    const nowMillis = now();
    const timestamp = FieldValue.serverTimestamp();
    return db.runTransaction(async (transaction) => {
      const [matchSnapshot, stateSnapshot] = await Promise.all([
        transaction.get(match),
        transaction.get(serverStateRef(matchId)),
      ]);
      if (!matchSnapshot.exists || !stateSnapshot.exists) {
        return { processed: false, reason: "missing" };
      }
      const state = stateSnapshot.data();
      const auditId = `turn_${state.turnNumber}_${state.stateVersion}`;
      const auditRef = match.collection("timeouts").doc(auditId);
      const auditSnapshot = await transaction.get(auditRef);
      if (auditSnapshot.exists) return { processed: false, reason: "duplicate" };

      const updated = expireTurn(state, nowMillis);
      if (!updated) {
        return { processed: false, reason: "not-expired" };
      }

      writeUpdatedMatch(transaction, updated, timestamp);
      transaction.create(auditRef, {
        type: "SCHEDULED_TIMEOUT",
        workerRunId,
        processed: true,
        previousTurnNumber: state.turnNumber,
        previousStateVersion: state.stateVersion,
        stateVersion: updated.stateVersion,
        currentPlayerUid: state.playerUids[state.currentPlayerIndex],
        createdAt: timestamp,
      });
      if (updated.result) {
        writeVerifiedResult(transaction, updated, timestamp, []);
        for (const playerUid of updated.playerUids) {
          transaction.set(activeRef(playerUid), {
            status: "IDLE",
            activeMatchId: null,
            lastMatchId: matchId,
            updatedAt: timestamp,
          }, { merge: true });
        }
      }
      return { processed: true, status: updated.status, stateVersion: updated.stateVersion };
    });
  }

  async function processDueTimeouts(options = {}) {
    const limit = options.limit ?? 100;
    if (!Number.isInteger(limit) || limit < 1 || limit > 500) {
      throw new HttpsError("invalid-argument", "Timeout batch size must be 1-500.");
    }
    const workerRunId = options.workerRunId || randomUUID();
    const cutoff = now();
    const dueMatches = await db.collection("onlineMatches")
      .where("status", "==", "ACTIVE")
      .where("turnDeadlineMillis", "<=", cutoff)
      .orderBy("turnDeadlineMillis", "asc")
      .limit(limit)
      .get();
    const results = await Promise.allSettled(dueMatches.docs.map((snapshot) =>
      processScheduledTimeout(snapshot.id, workerRunId)
    ));
    const failures = results.filter((result) => result.status === "rejected");
    if (failures.length) {
      throw new AggregateError(
        failures.map((result) => result.reason),
        `${failures.length} expired match timeout(s) could not be processed.`
      );
    }
    return {
      scanned: dueMatches.size,
      processed: results.filter((result) => result.status === "fulfilled" && result.value.processed).length,
      skipped: results.filter((result) => result.status === "fulfilled" && !result.value.processed).length,
    };
  }

  async function getMatchHistory(request) {
    validateData(request.data || {}, ["pageSize", "beforeCompletedAtMillis", "beforeMatchId"]);
    const uid = uidFrom(request);
    const pageSize = request.data.pageSize ?? 20;
    const beforeMillis = request.data.beforeCompletedAtMillis;
    const beforeMatchId = request.data.beforeMatchId;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > MAX_HISTORY_PAGE_SIZE) {
      throw new HttpsError("invalid-argument", `pageSize must be 1-${MAX_HISTORY_PAGE_SIZE}.`);
    }
    if (
      (beforeMillis === undefined) !== (beforeMatchId === undefined)
      || (beforeMillis !== undefined
        && (!Number.isSafeInteger(beforeMillis)
          || typeof beforeMatchId !== "string"
          || !/^[A-Za-z0-9_-]{8,80}$/.test(beforeMatchId)))
    ) {
      throw new HttpsError("invalid-argument", "Match history cursor is invalid.");
    }
    let query = db.collection("onlineMatchHistory").doc(uid).collection("matches")
      .orderBy("completedAt", "desc")
      .orderBy(FieldPath.documentId(), "desc")
      .limit(pageSize + 1);
    if (beforeMillis !== undefined) {
      query = query.startAfter(new Date(beforeMillis), beforeMatchId);
    }
    const snapshot = await query.get();
    const hasMore = snapshot.docs.length > pageSize;
    const docs = snapshot.docs.slice(0, pageSize);
    const last = docs[docs.length - 1];
    return {
      matches: docs.map((doc) => {
        const record = doc.data();
        return {
          matchId: record.matchId,
          status: record.status,
          verified: record.verified,
          outcome: record.outcome,
          winnerUid: record.winnerUid,
          finalPosition: record.finalPosition,
          durationMillis: record.durationMillis,
          completionReason: record.completionReason,
          completedAtMillis: record.completedAt?.toMillis?.() ?? null,
          players: record.players,
        };
      }),
      nextCursor: hasMore && last
        ? {
          beforeCompletedAtMillis: last.get("completedAt").toMillis(),
          beforeMatchId: last.id,
        }
        : null,
    };
  }

  return {
    cancelMatchmaking,
    createPrivateRoom,
    getMatchHistory,
    getPrivateRoom,
    heartbeatOnlineMatch,
    joinMatchmaking,
    joinPrivateRoom,
    leavePrivateRoom,
    observeOnlinePlayer,
    processExpiredTurn,
    processDueTimeouts,
    reconnectToMatch,
    setPrivateRoomReady,
    startPrivateRoom,
    startTournamentMatch,
    updatePrivateRoomRules,
    submitMatchAction,
  };
}

function generateRoomCode() {
  const bytes = randomBytes(ROOM_CODE_LENGTH);
  return Array.from(bytes, (byte) => ROOM_CODE_ALPHABET[byte % ROOM_CODE_ALPHABET.length]).join("");
}

module.exports = {
  ROOM_CODE_LENGTH,
  createMultiplayerService,
  generateRoomCode,
};
