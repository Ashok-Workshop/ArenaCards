const test = require("node:test");
const assert = require("node:assert/strict");
const {
  FIREBASE_PROJECT_ID,
  FIRESTORE_DATABASE_ID,
  FUNCTIONS_REGION,
  createFirestoreClient,
  validateRuntimeProject,
} = require("./firestore-client");
const callables = require("./index");

test("Firestore client explicitly selects the verified project and named database", async () => {
  const firestore = createFirestoreClient();
  try {
    assert.equal(FIREBASE_PROJECT_ID, "arenacards-f3279");
    assert.equal(FIRESTORE_DATABASE_ID, "unoashok");
    assert.equal(FUNCTIONS_REGION, "us-central1");
    assert.equal(firestore.projectId, "arenacards-f3279");
    assert.equal(firestore.databaseId, "unoashok");
  } finally {
    await firestore.terminate();
  }
});

test("Functions reject deployment into a different Firebase project", () => {
  assert.doesNotThrow(() => validateRuntimeProject("arenacards-f3279"));
  assert.doesNotThrow(() => validateRuntimeProject(undefined));
  assert.throws(
    () => validateRuntimeProject("unexpected-project"),
    /Cloud Functions project mismatch/
  );
});

test("all callable services remain in the configured Functions region", () => {
  for (const name of [
    "checkUsernameAvailability",
    "createPlayerProfile",
    "updatePlayerProfile",
    "searchPublicPlayers",
    "getArenaAccount",
    "claimDailyReward",
    "claimMissionReward",
    "getWalletTransactions",
    "joinMatchmaking",
    "cancelMatchmaking",
    "createPrivateRoom",
    "joinPrivateRoom",
    "getPrivateRoom",
    "setPrivateRoomReady",
    "leavePrivateRoom",
    "startPrivateRoom",
    "observeOnlinePlayer",
    "heartbeatOnlineMatch",
    "reconnectToMatch",
    "submitMatchAction",
    "processExpiredTurn",
    "getMatchHistory",
    "startTournamentMatch",
    "getCompetitiveLeaderboard",
    "getFriendsLeaderboard",
    "getMyCompetitiveProfile",
    "getCurrentSeason",
    "createCompetitiveSeason",
    "activateCompetitiveSeason",
    "finalizeCompetitiveSeason",
    "listCompetitiveSeasonHistory",
    "listTournaments",
    "createTournament",
    "registerTournament",
    "cancelTournamentRegistration",
    "startTournament",
    "getTournament",
    "searchPlayerByGamerId",
    "sendFriendRequest",
    "acceptFriendRequest",
    "rejectFriendRequest",
    "cancelFriendRequest",
    "listFriendRequests",
    "listFriends",
    "removeFriend",
    "blockPlayer",
    "unblockPlayer",
    "listBlockedPlayers",
    "listConversations",
    "updatePlayerPresence",
    "inviteFriendToRoom",
    "listRoomInvitations",
    "acceptRoomInvitation",
    "rejectRoomInvitation",
    "cancelRoomInvitation",
    "openDirectConversation",
    "sendDirectMessage",
    "getDirectMessages",
    "markConversationRead",
    "sendPrivateRoomMessage",
    "getPrivateRoomMessages",
    "reportPlayer",
    "listMyReports",
    "reviewReport",
    "registerNotificationToken",
    "removeNotificationToken",
    "setNotificationPreferences",
    "getNotificationPreferences",
    "listInAppNotifications",
    "markInAppNotificationRead",
  ]) {
    assert.deepEqual(callables[name].__endpoint.region, ["us-central1"]);
  }
});
