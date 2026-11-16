const { randomInt } = require("node:crypto");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { getApp, getApps, initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");
const Filter = require("bad-words");
const {
  FieldPath,
  FieldValue,
  FUNCTIONS_REGION,
  FIREBASE_PROJECT_ID,
  createFirestoreClient,
} = require("./firestore-client");
const {
  containsProfanity,
  formatGamerId,
  normalizeUsername,
  playerLevel,
  validateUsername,
} = require("./profile-domain");
const { createWalletService } = require("./wallet-service");
const { createMultiplayerService } = require("./multiplayer-service");
const { createSocialService } = require("./social-service");
const { createCompetitiveService } = require("./competitive-service");

const db = createFirestoreClient();
const adminApp = getApps().length
  ? getApp()
  : initializeApp({ projectId: FIREBASE_PROJECT_ID });
const profanityFilter = new Filter();
const walletService = createWalletService(db);
const competitiveService = createCompetitiveService(db);
const multiplayerService = createMultiplayerService(db, {
  prepareCompetitiveCompletion: competitiveService.prepareMatchCompletion,
  writeCompetitiveCompletion: competitiveService.writeMatchCompletion,
});
const socialService = createSocialService(db, { sendPush: sendSocialPush });
const ENFORCE_APP_CHECK = process.env.ARENA_ENFORCE_APP_CHECK === "true";

const AVATAR_IDS = new Set([
  "ember",
  "tide",
  "grove",
  "sun",
  "comet",
  "crown",
  "spark",
  "wild",
]);
const VISIBILITIES = new Set(["public", "private"]);

async function sendSocialPush(tokens, payload) {
  const invalidTokens = [];
  for (let offset = 0; offset < tokens.length; offset += 500) {
    const batch = tokens.slice(offset, offset + 500);
    const result = await getMessaging(adminApp).sendEachForMulticast({
      tokens: batch,
      data: Object.fromEntries(
        Object.entries(payload).map(([key, value]) => [
          key,
          typeof value === "string" ? value : JSON.stringify(value),
        ])
      ),
      android: { priority: "high" },
    });
    result.responses.forEach((response, index) => {
      if (!response.success && [
        "messaging/invalid-registration-token",
        "messaging/registration-token-not-registered",
      ].includes(response.error?.code)) {
        invalidTokens.push(batch[index]);
      }
    });
  }
  return { invalidTokens };
}

function requireUid(request) {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign in before managing a player profile.");
  }
  const firebaseProvider = request.auth.token.firebase?.sign_in_provider;
  if (firebaseProvider === "password" && request.auth.token.email_verified !== true) {
    throw new HttpsError(
      "permission-denied",
      "Verify your email address before creating or editing a player profile."
    );
  }
  return uid;
}

function validateProfileInput(data) {
  if (typeof data?.username !== "string") {
    throw new HttpsError("invalid-argument", "Username must be text.");
  }
  const username = validateUsername(data?.username);
  if (!username.valid) {
    throw new HttpsError(
      "invalid-argument",
      username.reason === "reserved"
        ? "That username is reserved. Choose another."
        : "Username must be 3-20 letters, numbers, or underscores."
    );
  }
  if (!isModeratedUsernameAllowed(username.normalized)) {
    throw new HttpsError("invalid-argument", "Choose a username that follows the community guidelines.");
  }

  if (typeof data?.displayName !== "string") {
    throw new HttpsError("invalid-argument", "Display name must be text.");
  }
  const displayName = data.displayName.trim();
  if (displayName.length < 1 || displayName.length > 30) {
    throw new HttpsError("invalid-argument", "Display name must be 1-30 characters.");
  }

  const avatarId = typeof data?.avatarId === "string" ? data.avatarId : "";
  if (!AVATAR_IDS.has(avatarId)) {
    throw new HttpsError("invalid-argument", "Choose an available avatar.");
  }
  return { username: String(data.username).trim(), usernameNormalized: username.normalized, displayName, avatarId };
}

function isModeratedUsernameAllowed(normalizedUsername) {
  return !containsProfanity(normalizedUsername, profanityFilter);
}

function callableOptions() {
  return {
    region: FUNCTIONS_REGION,
    enforceAppCheck: ENFORCE_APP_CHECK,
  };
}

function profileResponse(uid, profile) {
  return {
    uid,
    gamerId: profile.gamerId,
    username: profile.username,
    usernameNormalized: profile.usernameNormalized,
    displayName: profile.displayName,
    avatarId: profile.avatarId,
    createdAtMillis: profile.createdAt?.toMillis?.() ?? Date.now(),
    updatedAtMillis: profile.updatedAt?.toMillis?.() ?? Date.now(),
    accountStatus: profile.accountStatus,
    profileVisibility: profile.profileVisibility,
    totalMatches: profile.totalMatches,
    wins: profile.wins,
    losses: profile.losses,
    draws: profile.draws,
    experiencePoints: profile.experiencePoints,
    playerLevel: profile.playerLevel,
  };
}

exports.checkUsernameAvailability = onCall(callableOptions(), async (request) => {
  const uid = requireUid(request);
  const username = validateUsername(request.data?.username);
  if (!username.valid || !isModeratedUsernameAllowed(username.normalized)) {
    return {
      available: false,
      reason: username.reason ?? "policy",
    };
  }
  const reservation = await db.collection("usernames").doc(username.normalized).get();
  return { available: !reservation.exists || reservation.get("uid") === uid };
});

exports.createPlayerProfile = onCall(callableOptions(), async (request) => {
  const uid = requireUid(request);
  const input = validateProfileInput(request.data);
  const userRef = db.collection("users").doc(uid);
  const usernameRef = db.collection("usernames").doc(input.usernameNormalized);
  const privateRef = db.collection("privateAccounts").doc(uid);
  const publicRef = db.collection("publicPlayers").doc(uid);

  return db.runTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
    if (userSnapshot.exists) {
      return profileResponse(uid, userSnapshot.data());
    }

    const usernameSnapshot = await transaction.get(usernameRef);
    if (usernameSnapshot.exists && usernameSnapshot.get("uid") !== uid) {
      throw new HttpsError("already-exists", "That username is already taken.");
    }

    let gamerId;
    let gamerIdRef;
    for (let attempt = 0; attempt < 20; attempt += 1) {
      const candidate = formatGamerId(randomInt(0, 1_000_000));
      const candidateRef = db.collection("gamerIds").doc(candidate);
      const candidateSnapshot = await transaction.get(candidateRef);
      if (!candidateSnapshot.exists) {
        gamerId = candidate;
        gamerIdRef = candidateRef;
        break;
      }
    }
    if (!gamerIdRef) {
      throw new HttpsError("resource-exhausted", "A Gamer ID could not be allocated. Try again.");
    }

    const now = FieldValue.serverTimestamp();
    const profile = {
      ...input,
      gamerId,
      createdAt: now,
      updatedAt: now,
      accountStatus: "active",
      profileVisibility: "private",
      totalMatches: 0,
      wins: 0,
      losses: 0,
      draws: 0,
      experiencePoints: 0,
      playerLevel: playerLevel(0),
    };
    transaction.create(usernameRef, { uid, username: input.username, createdAt: now });
    transaction.create(gamerIdRef, { uid, createdAt: now });
    transaction.create(userRef, profile);
    transaction.create(db.collection("competitiveProfiles").doc(uid), {
      uid,
      rating: 1_000,
      verifiedMatches: 0,
      wins: 0,
      losses: 0,
      winPercentage: 0,
      winRateEligible: false,
      bestRating: 1_000,
      bestRank: null,
      winStreak: 0,
      bestWinStreak: 0,
      isPublic: false,
      createdAt: now,
      updatedAt: now,
    });
    transaction.create(privateRef, {
      uid,
      accountStatus: "active",
      createdAt: now,
      updatedAt: now,
    });
    transaction.create(publicRef, publicProjection(profile));
    return profileResponse(uid, { ...profile, createdAt: new Date(), updatedAt: new Date() });
  });
});

exports.updatePlayerProfile = onCall(callableOptions(), async (request) => {
  const uid = requireUid(request);
  const input = validateProfileInput(request.data);
  const visibility = String(request.data?.profileVisibility ?? "");
  if (!VISIBILITIES.has(visibility)) {
    throw new HttpsError("invalid-argument", "Choose a valid profile visibility setting.");
  }

  const userRef = db.collection("users").doc(uid);
  const usernameRef = db.collection("usernames").doc(input.usernameNormalized);
  const publicRef = db.collection("publicPlayers").doc(uid);
  const competitiveRef = db.collection("competitiveProfiles").doc(uid);

  return db.runTransaction(async (transaction) => {
    const [userSnapshot, competitiveSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(competitiveRef),
    ]);
    if (!userSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Create a player profile before editing it.");
    }
    const oldProfile = userSnapshot.data();
    const oldUsernameRef = db.collection("usernames").doc(oldProfile.usernameNormalized);
    const [requestedNameSnapshot, oldNameSnapshot] = await Promise.all([
      transaction.get(usernameRef),
      transaction.get(oldUsernameRef),
    ]);
    if (requestedNameSnapshot.exists && requestedNameSnapshot.get("uid") !== uid) {
      throw new HttpsError("already-exists", "That username is already taken.");
    }

    const existing = userSnapshot.data();
    const now = FieldValue.serverTimestamp();
    const updated = {
      ...existing,
      ...input,
      profileVisibility: visibility,
      updatedAt: now,
    };

    if (oldProfile.usernameNormalized !== input.usernameNormalized) {
      transaction.set(usernameRef, { uid, username: input.username, createdAt: now });
      if (oldNameSnapshot.exists && oldNameSnapshot.get("uid") === uid) {
        transaction.delete(oldUsernameRef);
      }
    } else if (oldNameSnapshot.exists && oldNameSnapshot.get("uid") === uid) {
      transaction.set(oldUsernameRef, { ...oldNameSnapshot.data(), username: input.username });
    } else {
      transaction.create(usernameRef, { uid, username: input.username, createdAt: now });
    }

    transaction.set(userRef, updated);
    transaction.set(publicRef, publicProjection(updated));
    if (competitiveSnapshot.exists) {
      transaction.set(competitiveRef, {
        isPublic: visibility === "public",
        updatedAt: now,
      }, { merge: true });
    }
    return profileResponse(uid, { ...updated, updatedAt: new Date() });
  });
});

exports.searchPublicPlayers = onCall(callableOptions(), async (request) => {
  requireUid(request);
  const prefix = normalizeUsername(request.data?.usernamePrefix);
  if (!/^[a-z0-9_]{1,20}$/.test(prefix)) {
    throw new HttpsError("invalid-argument", "Enter a valid username search prefix.");
  }

  const matches = await db.collection("usernames")
    .orderBy(FieldPath.documentId())
    .startAt(prefix)
    .endAt(`${prefix}\uf8ff`)
    .limit(15)
    .get();
  const publicRefs = matches.docs.map((doc) => db.collection("publicPlayers").doc(doc.get("uid")));
  const profiles = publicRefs.length > 0 ? await db.getAll(...publicRefs) : [];
  return profiles
    .filter((snapshot) => snapshot.exists && snapshot.get("profileVisibility") === "public")
    .map((snapshot) => {
      const profile = snapshot.data();
      return {
        uid: snapshot.id,
        gamerId: profile.gamerId,
        username: profile.username,
        displayName: profile.displayName,
        avatarId: profile.avatarId,
        playerLevel: profile.playerLevel,
      };
    });

});

exports.getArenaAccount = onCall(callableOptions(), walletService.getArenaAccount);
exports.claimDailyReward = onCall(callableOptions(), walletService.claimDailyReward);
exports.claimMissionReward = onCall(callableOptions(), walletService.claimMissionReward);
exports.getWalletTransactions = onCall(callableOptions(), walletService.getWalletTransactions);
exports.joinMatchmaking = onCall(callableOptions(), multiplayerService.joinMatchmaking);
exports.cancelMatchmaking = onCall(callableOptions(), multiplayerService.cancelMatchmaking);
exports.createPrivateRoom = onCall(callableOptions(), multiplayerService.createPrivateRoom);
exports.joinPrivateRoom = onCall(callableOptions(), multiplayerService.joinPrivateRoom);
exports.getPrivateRoom = onCall(callableOptions(), multiplayerService.getPrivateRoom);
exports.setPrivateRoomReady = onCall(callableOptions(), multiplayerService.setPrivateRoomReady);
exports.leavePrivateRoom = onCall(callableOptions(), multiplayerService.leavePrivateRoom);
exports.startPrivateRoom = onCall(callableOptions(), multiplayerService.startPrivateRoom);
exports.updatePrivateRoomRules = onCall(callableOptions(), multiplayerService.updatePrivateRoomRules);
exports.observeOnlinePlayer = onCall(callableOptions(), multiplayerService.observeOnlinePlayer);
exports.heartbeatOnlineMatch = onCall(callableOptions(), multiplayerService.heartbeatOnlineMatch);
exports.reconnectToMatch = onCall(callableOptions(), multiplayerService.reconnectToMatch);
exports.submitMatchAction = onCall(callableOptions(), multiplayerService.submitMatchAction);
exports.processExpiredTurn = onCall(callableOptions(), multiplayerService.processExpiredTurn);
exports.getMatchHistory = onCall(callableOptions(), multiplayerService.getMatchHistory);
exports.startTournamentMatch = onCall(
  callableOptions(),
  multiplayerService.startTournamentMatch
);
exports.getCompetitiveLeaderboard = onCall(
  callableOptions(),
  competitiveService.getCompetitiveLeaderboard
);
exports.getFriendsLeaderboard = onCall(
  callableOptions(),
  competitiveService.getFriendsLeaderboard
);
exports.getMyCompetitiveProfile = onCall(
  callableOptions(),
  competitiveService.getMyCompetitiveProfile
);
exports.getCurrentSeason = onCall(callableOptions(), competitiveService.getCurrentSeason);
exports.createCompetitiveSeason = onCall(callableOptions(), competitiveService.createSeason);
exports.activateCompetitiveSeason = onCall(callableOptions(), competitiveService.activateSeason);
exports.finalizeCompetitiveSeason = onCall(callableOptions(), competitiveService.finalizeSeason);
exports.listCompetitiveSeasonHistory = onCall(
  callableOptions(),
  competitiveService.listSeasonHistory
);
exports.listTournaments = onCall(callableOptions(), competitiveService.listTournaments);
exports.createTournament = onCall(callableOptions(), competitiveService.createTournament);
exports.registerTournament = onCall(callableOptions(), competitiveService.registerTournament);
exports.cancelTournamentRegistration = onCall(
  callableOptions(),
  competitiveService.cancelTournamentRegistration
);
exports.startTournament = onCall(callableOptions(), competitiveService.startTournament);
exports.getTournament = onCall(callableOptions(), competitiveService.getTournament);

exports.searchPlayerByGamerId = onCall(callableOptions(), socialService.searchPlayerByGamerId);
exports.sendFriendRequest = onCall(callableOptions(), socialService.sendFriendRequest);
exports.acceptFriendRequest = onCall(callableOptions(), socialService.acceptFriendRequest);
exports.rejectFriendRequest = onCall(callableOptions(), socialService.rejectFriendRequest);
exports.cancelFriendRequest = onCall(callableOptions(), socialService.cancelFriendRequest);
exports.listFriendRequests = onCall(callableOptions(), socialService.listFriendRequests);
exports.listFriends = onCall(callableOptions(), socialService.listFriends);
exports.removeFriend = onCall(callableOptions(), socialService.removeFriend);
exports.blockPlayer = onCall(callableOptions(), socialService.blockPlayer);
exports.unblockPlayer = onCall(callableOptions(), socialService.unblockPlayer);
exports.listBlockedPlayers = onCall(callableOptions(), socialService.listBlockedPlayers);
exports.listConversations = onCall(callableOptions(), socialService.listConversations);
exports.updatePlayerPresence = onCall(callableOptions(), socialService.updatePresence);
exports.inviteFriendToRoom = onCall(callableOptions(), socialService.inviteFriendToRoom);
exports.listRoomInvitations = onCall(callableOptions(), socialService.listRoomInvitations);
exports.acceptRoomInvitation = onCall(callableOptions(), socialService.acceptRoomInvitation);
exports.rejectRoomInvitation = onCall(callableOptions(), socialService.rejectRoomInvitation);
exports.cancelRoomInvitation = onCall(callableOptions(), socialService.cancelRoomInvitation);
exports.openDirectConversation = onCall(callableOptions(), socialService.openConversation);
exports.sendDirectMessage = onCall(callableOptions(), socialService.sendDirectMessage);
exports.getDirectMessages = onCall(callableOptions(), socialService.getDirectMessages);
exports.markConversationRead = onCall(callableOptions(), socialService.markConversationRead);
exports.sendPrivateRoomMessage = onCall(callableOptions(), socialService.sendRoomMessage);
exports.getPrivateRoomMessages = onCall(callableOptions(), socialService.getRoomMessages);
exports.reportPlayer = onCall(callableOptions(), socialService.reportPlayer);
exports.listMyReports = onCall(callableOptions(), socialService.listReports);
exports.reviewReport = onCall(callableOptions(), socialService.reviewReport);
exports.registerNotificationToken = onCall(callableOptions(), socialService.registerNotificationToken);
exports.removeNotificationToken = onCall(callableOptions(), socialService.removeNotificationToken);
exports.setNotificationPreferences = onCall(
  callableOptions(),
  socialService.setNotificationPreferences
);
exports.getNotificationPreferences = onCall(
  callableOptions(),
  socialService.getNotificationPreferences
);
exports.listInAppNotifications = onCall(callableOptions(), socialService.listNotifications);
exports.markInAppNotificationRead = onCall(callableOptions(), socialService.markNotificationRead);

exports.processMultiplayerTimeouts = onSchedule({
  region: FUNCTIONS_REGION,
  schedule: "every 1 minutes",
  retryCount: 3,
  maxInstances: 1,
  timeZone: "UTC",
}, async () => {
  const result = await multiplayerService.processDueTimeouts({ limit: 100 });
  const invitations = await socialService.expireRoomInvitations({ limit: 100 });
  console.info("Processed scheduled multiplayer timeouts.", result);
  return { ...result, invitations };
});

exports.finalizeCompetitiveSeasons = onSchedule({
  region: FUNCTIONS_REGION,
  schedule: "every 60 minutes",
  retryCount: 3,
  maxInstances: 1,
  timeZone: "UTC",
}, async () => {
  const result = await competitiveService.finalizeDueSeasons();
  console.info("Processed due competitive seasons.", result);
  return result;
});

function publicProjection(profile) {
  return {
    gamerId: profile.gamerId,
    username: profile.username,
    usernameNormalized: profile.usernameNormalized,
    displayName: profile.displayName,
    avatarId: profile.avatarId,
    createdAt: profile.createdAt,
    updatedAt: profile.updatedAt,
    profileVisibility: profile.profileVisibility,
    totalMatches: profile.totalMatches,
    wins: profile.wins,
    losses: profile.losses,
    draws: profile.draws,
    experiencePoints: profile.experiencePoints,
    playerLevel: profile.playerLevel,
  };
}
