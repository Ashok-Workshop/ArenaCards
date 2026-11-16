const { HttpsError } = require("firebase-functions/v2/https");
const { FieldPath, FieldValue } = require("./firestore-client");

const DAY_MILLIS = 24 * 60 * 60 * 1000;
const CLAIM_RATE_LIMIT_MILLIS = 1_000;
const MAX_PAGE_SIZE = 50;
const DAILY_REWARD_COINS = [50, 75, 100, 150, 200, 250, 500];
const MISSIONS = Object.freeze([
  {
    id: "daily_login",
    title: "Check in at the arena",
    description: "Claim today's daily login reward.",
    type: "DAILY_LOGIN",
    targetValue: 1,
    rewardCoins: 0,
    rewardXp: 25,
    enabled: true,
  },
  {
    id: "three_day_streak",
    title: "Build a streak",
    description: "Claim daily rewards on three consecutive UTC days.",
    type: "LOGIN_STREAK",
    targetValue: 3,
    rewardCoins: 30,
    rewardXp: 75,
    enabled: true,
  },
  {
    id: "verified_online_match",
    title: "Play a verified online match",
    description: "Online match verification is not available yet.",
    type: "VERIFIED_ONLINE_MATCH",
    targetValue: 1,
    rewardCoins: 0,
    rewardXp: 0,
    enabled: false,
  },
]);
const ACHIEVEMENTS = Object.freeze([
  {
    id: "daily_streak_3",
    title: "Three-day arrival",
    description: "Claim daily rewards on three consecutive UTC days.",
    targetValue: 3,
  },
  {
    id: "daily_streak_7",
    title: "A week at the table",
    description: "Claim daily rewards on seven consecutive UTC days.",
    targetValue: 7,
  },
]);

function utcDayStart(nowMillis) {
  const date = new Date(nowMillis);
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
}

function utcDateKey(nowMillis) {
  return new Date(utcDayStart(nowMillis)).toISOString().slice(0, 10);
}

function playerLevel(experiencePoints) {
  return Math.floor(experiencePoints / 1_000) + 1;
}

function createWalletService(db, options = {}) {
  const now = options.now || Date.now;
  const claimRateLimitMillis = options.claimRateLimitMillis ?? CLAIM_RATE_LIMIT_MILLIS;

  function requireUid(request) {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign in to access your ArenaCards account.");
    }
    if (
      request.auth.token.firebase?.sign_in_provider === "password"
      && request.auth.token.email_verified !== true
    ) {
      throw new HttpsError(
        "permission-denied",
        "Verify your email address before claiming ArenaCards rewards."
      );
    }
    return uid;
  }

  function validatePayload(data, allowedKeys) {
    if (!data || typeof data !== "object" || Array.isArray(data)) {
      throw new HttpsError("invalid-argument", "Request data must be an object.");
    }
    const unknownKey = Object.keys(data).find((key) => !allowedKeys.includes(key));
    if (unknownKey) {
      throw new HttpsError("invalid-argument", "Request contains an unsupported field.");
    }
  }

  function validateRequestId(value) {
    if (typeof value !== "string" || !/^[A-Za-z0-9_-]{8,80}$/.test(value)) {
      throw new HttpsError("invalid-argument", "A valid request ID is required.");
    }
    return value;
  }

  function walletRef(uid) {
    return db.collection("wallets").doc(uid);
  }

  function missionRef(uid, day, missionId) {
    return db.collection("missions").doc(uid)
      .collection("days").doc(day)
      .collection("items").doc(missionId);
  }

  function transactionRef(uid, transactionId) {
    return walletRef(uid).collection("transactions").doc(transactionId);
  }

  function walletValues(snapshot) {
    if (!snapshot.exists) {
      return {
        coinsBalance: 0,
        totalCoinsEarned: 0,
        totalCoinsSpent: 0,
        createdAt: FieldValue.serverTimestamp(),
      };
    }
    const data = snapshot.data();
    const values = {
      coinsBalance: data.coinsBalance,
      totalCoinsEarned: data.totalCoinsEarned,
      totalCoinsSpent: data.totalCoinsSpent,
      createdAt: data.createdAt,
    };
    if (
      !Number.isSafeInteger(values.coinsBalance)
      || values.coinsBalance < 0
      || !Number.isSafeInteger(values.totalCoinsEarned)
      || values.totalCoinsEarned < 0
      || !Number.isSafeInteger(values.totalCoinsSpent)
      || values.totalCoinsSpent < 0
      || values.totalCoinsEarned - values.totalCoinsSpent !== values.coinsBalance
    ) {
      throw new HttpsError("internal", "Wallet integrity check failed. Contact support.");
    }
    return values;
  }

  function timestampMillis(value) {
    if (value && typeof value.toMillis === "function") return value.toMillis();
    if (value instanceof Date) return value.getTime();
    return null;
  }

  function experiencePointsValue(progressionSnapshot, userSnapshot) {
    const value = progressionSnapshot.exists
      ? progressionSnapshot.get("experiencePoints")
      : (userSnapshot.exists ? userSnapshot.get("experiencePoints") : 0);
    if (!Number.isSafeInteger(value) || value < 0) {
      throw new HttpsError("internal", "Progression integrity check failed. Contact support.");
    }
    return value;
  }

  function requireSafeAward(balance, earned, amount) {
    const newBalance = balance + amount;
    const newEarned = earned + amount;
    if (!Number.isSafeInteger(newBalance) || !Number.isSafeInteger(newEarned)) {
      throw new HttpsError("resource-exhausted", "Wallet balance has reached its supported limit.");
    }
    return { newBalance, newEarned };
  }

  function walletResponse(snapshot) {
    const values = walletValues(snapshot);
    return {
      coinsBalance: values.coinsBalance,
      totalCoinsEarned: values.totalCoinsEarned,
      totalCoinsSpent: values.totalCoinsSpent,
      createdAtMillis: timestampMillis(values.createdAt),
      updatedAtMillis: timestampMillis(snapshot.get("updatedAt")),
    };
  }

  function missionResponse(definition, snapshot, dayStart, expiryMillis) {
    const state = snapshot.exists ? snapshot.data() : {};
    const progress = state.currentProgress === undefined ? 0 : state.currentProgress;
    if (!Number.isSafeInteger(progress) || progress < 0 || progress > definition.targetValue) {
      throw new HttpsError("internal", "Mission progress integrity check failed.");
    }
    return {
      id: definition.id,
      title: definition.title,
      description: definition.description,
      type: definition.type,
      targetValue: definition.targetValue,
      currentProgress: progress,
      rewardAmount: definition.rewardCoins,
      rewardXp: definition.rewardXp,
      startTimeMillis: dayStart,
      expiryTimeMillis: expiryMillis,
      completed: state.completed === true,
      claimed: state.claimed === true,
    };
  }

  async function getArenaAccount(request) {
    validatePayload(request.data || {}, []);
    const uid = requireUid(request);
    const currentTime = now();
    const todayStart = utcDayStart(currentTime);
    const today = utcDateKey(currentTime);
    const expiryMillis = todayStart + DAY_MILLIS;
    const walletDocument = walletRef(uid);

    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(walletDocument);
      if (!snapshot.exists) {
        transaction.create(walletDocument, {
          coinsBalance: 0,
          totalCoinsEarned: 0,
          totalCoinsSpent: 0,
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else {
        walletValues(snapshot);
      }
    });

    const dailyRef = db.collection("dailyRewards").doc(uid);
    const progressionRef = db.collection("progression").doc(uid);
    const userRef = db.collection("users").doc(uid);
    const missionDefinitions = MISSIONS.filter((mission) => mission.enabled);
    const achievementRefs = ACHIEVEMENTS.map((achievement) =>
      progressionRef.collection("achievements").doc(achievement.id));
    const achievementHistoryRefs = ACHIEVEMENTS.map((achievement) =>
      progressionRef.collection("achievementHistory").doc(achievement.id));
    const refs = [
      walletDocument,
      dailyRef,
      progressionRef,
      userRef,
      ...missionDefinitions.map((mission) => missionRef(uid, today, mission.id)),
      ...achievementRefs,
      ...achievementHistoryRefs,
    ];
    const snapshots = await db.getAll(...refs);
    const [walletSnapshot, dailySnapshot, progressionSnapshot, userSnapshot] = snapshots;
    const dailyData = dailySnapshot.exists ? dailySnapshot.data() : {};
    const experiencePoints = experiencePointsValue(progressionSnapshot, userSnapshot);
    const level = playerLevel(experiencePoints);
    const lastClaimDate = dailyData.lastClaimDate || null;
    const lastClaimTime = lastClaimDate ? Date.parse(`${lastClaimDate}T00:00:00.000Z`) : null;
    const streakDays = dailyData.streakDays || 0;
    const todayClaimed = lastClaimDate === today;
    const nextStreak = todayClaimed
      ? streakDays
      : (lastClaimTime === todayStart - DAY_MILLIS ? streakDays + 1 : 1);
    const alreadyClaimedToday = todayClaimed;
    const missionStart = 4;
    const missions = missionDefinitions.map((mission, index) =>
      missionResponse(
        mission,
        snapshots[missionStart + index],
        todayStart,
        expiryMillis
      ));
    const achievements = ACHIEVEMENTS.map((achievement, index) => {
      const snapshot = snapshots[missionStart + missionDefinitions.length + index];
      const progress = snapshot.exists ? snapshot.get("progress") || 0 : 0;
      if (!Number.isSafeInteger(progress) || progress < 0 || progress > achievement.targetValue) {
        throw new HttpsError("internal", "Achievement progress integrity check failed.");
      }
      return {
        id: achievement.id,
        title: achievement.title,
        description: achievement.description,
        progress,
        targetValue: achievement.targetValue,
        unlocked: snapshot.exists && snapshot.get("unlocked") === true,
        unlockedAtMillis: snapshot.exists
          ? timestampMillis(snapshot.get("unlockedAt"))
          : null,
      };
    });
    const historyStart = missionStart + missionDefinitions.length + ACHIEVEMENTS.length;
    const achievementUnlockHistory = ACHIEVEMENTS.flatMap((achievement, index) => {
      const history = snapshots[historyStart + index];
      if (!history.exists) return [];
      return [{
        id: achievement.id,
        title: achievement.title,
        unlockedAtMillis: timestampMillis(history.get("unlockedAt")),
      }];
    });
    const earned = walletResponse(walletSnapshot);

    return {
      wallet: earned,
      dailyReward: {
        lastClaimDate,
        streakDays,
        claimAvailable: !alreadyClaimedToday,
        nextRewardCoins: alreadyClaimedToday
          ? 0
          : DAILY_REWARD_COINS[Math.min(nextStreak, DAILY_REWARD_COINS.length) - 1],
        nextClaimAtMillis: expiryMillis,
        resetTimezone: "UTC",
      },
      missions,
      progression: {
        experiencePoints,
        level,
        xpToNextLevel: level * 1_000 - experiencePoints,
      },
      achievements,
      achievementUnlockHistory,
    };
  }

  async function claimDailyReward(request) {
    validatePayload(request.data, ["requestId"]);
    const uid = requireUid(request);
    const requestId = validateRequestId(request.data.requestId);
    const currentTime = now();
    const todayStart = utcDayStart(currentTime);
    const today = utcDateKey(currentTime);
    const yesterday = utcDateKey(todayStart - DAY_MILLIS);
    const rewardRef = db.collection("dailyRewards").doc(uid);
    const claimRef = db.collection("dailyRewardClaims").doc(uid).collection("days").doc(today);
    const walletDocument = walletRef(uid);
    const progressionRef = db.collection("progression").doc(uid);
    const userRef = db.collection("users").doc(uid);
    const publicRef = db.collection("publicPlayers").doc(uid);
    const rateLimitRef = db.collection("walletRateLimits").doc(uid)
      .collection("operations").doc("claims");
    const experienceEventRef = progressionRef.collection("experienceEvents").doc(`daily_${today}`);
    const missionDocuments = MISSIONS.filter((mission) => mission.enabled).map((mission) => ({
      definition: mission,
      ref: missionRef(uid, today, mission.id),
    }));
    const achievementDocuments = ACHIEVEMENTS.map((achievement) => ({
      definition: achievement,
      ref: progressionRef.collection("achievements").doc(achievement.id),
    }));
    const achievementHistoryDocuments = ACHIEVEMENTS.map((achievement) => ({
      definition: achievement,
      ref: progressionRef.collection("achievementHistory").doc(achievement.id),
    }));
    const currentTimeStamp = FieldValue.serverTimestamp();

    return db.runTransaction(async (transaction) => {
      const refs = [
        claimRef,
        rewardRef,
        walletDocument,
        progressionRef,
        userRef,
        publicRef,
        rateLimitRef,
        ...missionDocuments.map((item) => item.ref),
        ...achievementDocuments.map((item) => item.ref),
        ...achievementHistoryDocuments.map((item) => item.ref),
      ];
      const snapshots = await Promise.all(refs.map((ref) => transaction.get(ref)));
      const [
        claimSnapshot,
        rewardSnapshot,
        walletSnapshot,
        progressionSnapshot,
        userSnapshot,
        publicSnapshot,
        rateLimitSnapshot,
        ...remainingSnapshots
      ] = snapshots;
      if (claimSnapshot.exists) {
        return {
          claimed: false,
          alreadyClaimed: true,
          streakDays: claimSnapshot.get("streakDays"),
          rewardCoins: claimSnapshot.get("rewardCoins"),
          coinsBalance: walletValues(walletSnapshot).coinsBalance,
          nextClaimAtMillis: todayStart + DAY_MILLIS,
        };
      }

      const rewardData = rewardSnapshot.exists ? rewardSnapshot.data() : {};
      if (rewardData.lastClaimDate === today) {
        return {
          claimed: false,
          alreadyClaimed: true,
          streakDays: rewardData.streakDays || 0,
          rewardCoins: 0,
          coinsBalance: walletValues(walletSnapshot).coinsBalance,
          nextClaimAtMillis: todayStart + DAY_MILLIS,
        };
      }

      const rateLimitData = rateLimitSnapshot.exists ? rateLimitSnapshot.data() : {};
      if (
        Number.isSafeInteger(rateLimitData.lastOperationAtMillis)
        && currentTime - rateLimitData.lastOperationAtMillis < claimRateLimitMillis
      ) {
        throw new HttpsError("resource-exhausted", "Please wait before claiming another reward.");
      }

      const streakDays = rewardData.lastClaimDate === yesterday
        ? (rewardData.streakDays || 0) + 1
        : 1;
      const rewardCoins = DAILY_REWARD_COINS[
        Math.min(streakDays, DAILY_REWARD_COINS.length) - 1
      ];
      const wallet = walletValues(walletSnapshot);
      const transactionId = `daily_${today}`;
      const ledgerRef = transactionRef(uid, transactionId);
      const previousMissionSnapshots = remainingSnapshots.slice(0, missionDocuments.length);
      const previousAchievementSnapshots = remainingSnapshots.slice(
        missionDocuments.length,
        missionDocuments.length + achievementDocuments.length
      );
      const achievementHistorySnapshots = remainingSnapshots.slice(
        missionDocuments.length + achievementDocuments.length
      );
      const experiencePoints = experiencePointsValue(progressionSnapshot, userSnapshot);
      const xpAward = 25;
      const newExperiencePoints = experiencePoints + xpAward;
      if (!Number.isSafeInteger(newExperiencePoints)) {
        throw new HttpsError("resource-exhausted", "Experience has reached its supported limit.");
      }
      const newLevel = playerLevel(newExperiencePoints);
      const { newBalance, newEarned } = requireSafeAward(
        wallet.coinsBalance,
        wallet.totalCoinsEarned,
        rewardCoins
      );

      transaction.set(walletDocument, {
        coinsBalance: newBalance,
        totalCoinsEarned: newEarned,
        totalCoinsSpent: wallet.totalCoinsSpent,
        createdAt: wallet.createdAt,
        updatedAt: currentTimeStamp,
      });
      transaction.create(ledgerRef, {
        transactionId,
        uid,
        type: "DAILY_REWARD",
        amount: rewardCoins,
        balanceBefore: wallet.coinsBalance,
        balanceAfter: newBalance,
        source: "daily_login",
        timestamp: currentTimeStamp,
        idempotencyKey: requestId,
        referenceId: today,
        status: "COMMITTED",
      });
      transaction.set(claimRef, {
        uid,
        claimDate: today,
        streakDays,
        rewardCoins,
        claimedAt: currentTimeStamp,
        idempotencyKey: requestId,
        transactionId,
      });
      transaction.set(rewardRef, {
        lastClaimDate: today,
        streakDays,
        lastClaimedAt: currentTimeStamp,
        updatedAt: currentTimeStamp,
      });
      transaction.set(rateLimitRef, {
        lastOperationAtMillis: currentTime,
        updatedAt: currentTimeStamp,
      });
      transaction.set(experienceEventRef, {
        eventId: `daily_${today}`,
        type: "DAILY_LOGIN",
        amount: xpAward,
        previousExperiencePoints: experiencePoints,
        newExperiencePoints,
        source: "daily_login",
        referenceId: today,
        timestamp: currentTimeStamp,
        status: "COMMITTED",
      });
      transaction.set(progressionRef, {
        experiencePoints: newExperiencePoints,
        level: newLevel,
        totalXpEarned: (progressionSnapshot.get("totalXpEarned") || experiencePoints) + xpAward,
        updatedAt: currentTimeStamp,
        ...(progressionSnapshot.exists ? {} : { createdAt: currentTimeStamp }),
      }, { merge: true });

      if (userSnapshot.exists) {
        transaction.set(userRef, {
          experiencePoints: newExperiencePoints,
          playerLevel: newLevel,
          updatedAt: currentTimeStamp,
        }, { merge: true });
      }
      if (publicSnapshot.exists) {
        transaction.set(publicRef, {
          experiencePoints: newExperiencePoints,
          playerLevel: newLevel,
          updatedAt: currentTimeStamp,
        }, { merge: true });
      }

      missionDocuments.forEach(({ definition, ref }, index) => {
        const progress = definition.id === "daily_login"
          ? 1
          : Math.min(streakDays, definition.targetValue);
        const previous = previousMissionSnapshots[index];
        transaction.set(ref, {
          missionId: definition.id,
          title: definition.title,
          description: definition.description,
          type: definition.type,
          targetValue: definition.targetValue,
          currentProgress: progress,
          rewardAmount: definition.rewardCoins,
          rewardXp: definition.rewardXp,
          startTimeMillis: todayStart,
          expiryTimeMillis: todayStart + DAY_MILLIS,
          completed: progress >= definition.targetValue,
          claimed: previous.exists && previous.get("claimed") === true,
          ...(previous.exists && previous.get("claimedAt")
            ? { claimedAt: previous.get("claimedAt") }
            : {}),
          updatedAt: currentTimeStamp,
          createdAt: previous.exists ? previous.get("createdAt") : currentTimeStamp,
        });
      });

      achievementDocuments.forEach(({ definition, ref }, index) => {
        const previous = previousAchievementSnapshots[index];
        const previousHistory = achievementHistorySnapshots[index];
        const previousProgress = previous.exists ? previous.get("progress") || 0 : 0;
        if (!Number.isSafeInteger(previousProgress) || previousProgress < 0) {
          throw new HttpsError("internal", "Achievement progress integrity check failed.");
        }
        const progress = Math.max(previousProgress, streakDays);
        const unlocked = previous.exists && previous.get("unlocked") === true
          || progress >= definition.targetValue;
        transaction.set(ref, {
          achievementId: definition.id,
          title: definition.title,
          description: definition.description,
          progress: Math.min(progress, definition.targetValue),
          targetValue: definition.targetValue,
          unlocked,
          ...(unlocked
            ? { unlockedAt: previous.exists && previous.get("unlockedAt")
              ? previous.get("unlockedAt")
              : currentTimeStamp }
            : {}),
          updatedAt: currentTimeStamp,
          createdAt: previous.exists ? previous.get("createdAt") : currentTimeStamp,
        });
        if (unlocked && !previousHistory.exists) {
          const historyRef = achievementHistoryDocuments[index].ref;
          transaction.create(historyRef, {
            achievementId: definition.id,
            title: definition.title,
            description: definition.description,
            unlockedAt: currentTimeStamp,
            progress: Math.min(progress, definition.targetValue),
            status: "UNLOCKED",
          });
        }
      });

      return {
        claimed: true,
        alreadyClaimed: false,
        streakDays,
        rewardCoins,
        coinsBalance: newBalance,
        experiencePoints: newExperiencePoints,
        level: newLevel,
        nextClaimAtMillis: todayStart + DAY_MILLIS,
      };
    });
  }

  async function claimMissionReward(request) {
    validatePayload(request.data, ["missionId", "requestId"]);
    const uid = requireUid(request);
    const missionId = request.data.missionId;
    const definition = MISSIONS.find((item) => item.id === missionId && item.enabled);
    if (!definition) {
      throw new HttpsError("invalid-argument", "That mission is not available.");
    }
    const requestId = validateRequestId(request.data.requestId);
    const currentTime = now();
    const todayStart = utcDayStart(currentTime);
    const today = utcDateKey(currentTime);
    const walletDocument = walletRef(uid);
    const progressionRef = db.collection("progression").doc(uid);
    const missionDocument = missionRef(uid, today, definition.id);
    const claimRef = db.collection("missionClaims").doc(uid)
      .collection("claims").doc(`${today}_${definition.id}`);
    const ledgerRef = transactionRef(uid, `mission_${today}_${definition.id}`);
    const experienceEventRef = progressionRef.collection("experienceEvents")
      .doc(`mission_${today}_${definition.id}`);
    const rewardRef = db.collection("dailyRewards").doc(uid);
    const userRef = db.collection("users").doc(uid);
    const publicRef = db.collection("publicPlayers").doc(uid);
    const rateLimitRef = db.collection("walletRateLimits").doc(uid)
      .collection("operations").doc("claims");
    const currentTimeStamp = FieldValue.serverTimestamp();

    return db.runTransaction(async (transaction) => {
      const refs = [
        claimRef,
        missionDocument,
        walletDocument,
        progressionRef,
        rewardRef,
        rateLimitRef,
        ledgerRef,
        userRef,
        publicRef,
      ];
      const [
        claimSnapshot,
        missionSnapshot,
        walletSnapshot,
        progressionSnapshot,
        rewardSnapshot,
        rateLimitSnapshot,
        ledgerSnapshot,
        userSnapshot,
        publicSnapshot,
      ] = await Promise.all(refs.map((ref) => transaction.get(ref)));

      if (claimSnapshot.exists) {
        return {
          claimed: false,
          alreadyClaimed: true,
          missionId,
          rewardAmount: claimSnapshot.get("rewardAmount"),
          rewardXp: claimSnapshot.get("rewardXp"),
          coinsBalance: walletValues(walletSnapshot).coinsBalance,
          experiencePoints: progressionSnapshot.get("experiencePoints") || 0,
        };
      }
      if (!missionSnapshot.exists || missionSnapshot.get("completed") !== true) {
        throw new HttpsError("failed-precondition", "This mission is not complete or has expired.");
      }
      if (missionSnapshot.get("claimed") === true || ledgerSnapshot.exists) {
        throw new HttpsError("failed-precondition", "This mission reward has already been claimed.");
      }

      const rateLimitData = rateLimitSnapshot.exists ? rateLimitSnapshot.data() : {};
      if (
        Number.isSafeInteger(rateLimitData.lastOperationAtMillis)
        && currentTime - rateLimitData.lastOperationAtMillis < claimRateLimitMillis
      ) {
        throw new HttpsError("resource-exhausted", "Please wait before claiming another reward.");
      }

      const wallet = walletValues(walletSnapshot);
      const rewardCoins = definition.rewardCoins;
      const rewardXp = definition.rewardXp;
      const experiencePoints = experiencePointsValue(progressionSnapshot, userSnapshot);
      const newExperiencePoints = experiencePoints + rewardXp;
      if (!Number.isSafeInteger(newExperiencePoints)) {
        throw new HttpsError("resource-exhausted", "Experience has reached its supported limit.");
      }
      const newLevel = playerLevel(newExperiencePoints);
      const { newBalance, newEarned } = requireSafeAward(
        wallet.coinsBalance,
        wallet.totalCoinsEarned,
        rewardCoins
      );

      transaction.set(walletDocument, {
        coinsBalance: newBalance,
        totalCoinsEarned: newEarned,
        totalCoinsSpent: wallet.totalCoinsSpent,
        createdAt: wallet.createdAt,
        updatedAt: currentTimeStamp,
      });
      if (rewardCoins > 0) {
        transaction.create(ledgerRef, {
          transactionId: ledgerRef.id,
          uid,
          type: "MISSION_REWARD",
          amount: rewardCoins,
          balanceBefore: wallet.coinsBalance,
          balanceAfter: newBalance,
          source: `mission:${missionId}`,
          timestamp: currentTimeStamp,
          idempotencyKey: requestId,
          referenceId: missionId,
          status: "COMMITTED",
        });
      }
      transaction.set(experienceEventRef, {
        eventId: experienceEventRef.id,
        type: "MISSION_CLAIM",
        amount: rewardXp,
        previousExperiencePoints: experiencePoints,
        newExperiencePoints,
        source: `mission:${missionId}`,
        referenceId: missionId,
        timestamp: currentTimeStamp,
        status: "COMMITTED",
      });
      transaction.set(progressionRef, {
        experiencePoints: newExperiencePoints,
        level: newLevel,
        totalXpEarned: (progressionSnapshot.get("totalXpEarned") || experiencePoints) + rewardXp,
        updatedAt: currentTimeStamp,
        ...(progressionSnapshot.exists ? {} : { createdAt: currentTimeStamp }),
      }, { merge: true });
      transaction.set(missionDocument, {
        claimed: true,
        claimedAt: currentTimeStamp,
        claimRequestId: requestId,
        updatedAt: currentTimeStamp,
      }, { merge: true });
      transaction.create(claimRef, {
        uid,
        missionId,
        claimDate: today,
        rewardAmount: rewardCoins,
        rewardXp,
        requestId,
        claimedAt: currentTimeStamp,
        transactionId: rewardCoins > 0 ? ledgerRef.id : null,
        status: "COMMITTED",
      });
      transaction.set(rateLimitRef, {
        lastOperationAtMillis: currentTime,
        updatedAt: currentTimeStamp,
      });
      if (userSnapshot.exists) {
        transaction.set(userRef, {
          experiencePoints: newExperiencePoints,
          playerLevel: newLevel,
          updatedAt: currentTimeStamp,
        }, { merge: true });
      }
      if (publicSnapshot.exists) {
        transaction.set(publicRef, {
          experiencePoints: newExperiencePoints,
          playerLevel: newLevel,
          updatedAt: currentTimeStamp,
        }, { merge: true });
      }

      return {
        claimed: true,
        alreadyClaimed: false,
        missionId,
        rewardAmount: rewardCoins,
        rewardXp,
        coinsBalance: newBalance,
        experiencePoints: newExperiencePoints,
        level: newLevel,
      };
    });
  }

  async function getWalletTransactions(request) {
    validatePayload(request.data || {}, [
      "pageSize",
      "beforeCreatedAtMillis",
      "beforeTransactionId",
    ]);
    const uid = requireUid(request);
    const pageSize = request.data?.pageSize ?? 20;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new HttpsError("invalid-argument", `pageSize must be between 1 and ${MAX_PAGE_SIZE}.`);
    }
    const beforeCreatedAtMillis = request.data?.beforeCreatedAtMillis;
    const beforeTransactionId = request.data?.beforeTransactionId;
    if (
      (beforeCreatedAtMillis === undefined) !== (beforeTransactionId === undefined)
      || (beforeCreatedAtMillis !== undefined
        && (!Number.isSafeInteger(beforeCreatedAtMillis)
          || typeof beforeTransactionId !== "string"
          || !/^[A-Za-z0-9_-]{1,120}$/.test(beforeTransactionId)))
    ) {
      throw new HttpsError("invalid-argument", "Transaction cursor is invalid.");
    }

    let query = walletRef(uid).collection("transactions")
      .orderBy("timestamp", "desc")
      .orderBy(FieldPath.documentId(), "desc")
      .limit(pageSize + 1);
    if (beforeCreatedAtMillis !== undefined) {
      query = query.startAfter(new Date(beforeCreatedAtMillis), beforeTransactionId);
    }
    const snapshot = await query.get();
    const hasMore = snapshot.docs.length > pageSize;
    const documents = snapshot.docs.slice(0, pageSize);
    const last = documents[documents.length - 1];
    return {
      transactions: documents.map((document) => {
        const data = document.data();
        return {
          transactionId: data.transactionId,
          type: data.type,
          amount: data.amount,
          balanceBefore: data.balanceBefore,
          balanceAfter: data.balanceAfter,
          source: data.source,
          timestampMillis: timestampMillis(data.timestamp),
          referenceId: data.referenceId,
          status: data.status,
        };
      }),
      nextCursor: hasMore && last
        ? {
          beforeCreatedAtMillis: timestampMillis(last.get("timestamp")),
          beforeTransactionId: last.id,
        }
        : null,
    };
  }

  return {
    getArenaAccount,
    claimDailyReward,
    claimMissionReward,
    getWalletTransactions,
  };
}

module.exports = {
  ACHIEVEMENTS,
  DAILY_REWARD_COINS,
  DAY_MILLIS,
  MISSIONS,
  CLAIM_RATE_LIMIT_MILLIS,
  createWalletService,
  playerLevel,
  utcDateKey,
  utcDayStart,
};
