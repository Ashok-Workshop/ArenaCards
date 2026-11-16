import assert from "node:assert/strict";
import { after, test } from "node:test";
import { deleteApp, initializeApp } from "firebase/app";
import {
  connectAuthEmulator,
  getAuth,
  signInAnonymously,
  signOut,
} from "firebase/auth";
import {
  connectFirestoreEmulator,
  doc,
  getDoc,
  getFirestore,
  setDoc,
} from "firebase/firestore";

const PROJECT_ID = "demo-arenacards";
const DATABASE_ID = "unoashok";
const FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080";
if (FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080") {
  throw new Error("Rules tests require the local Firestore Emulator at 127.0.0.1:8080.");
}
const AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const app = initializeApp(
  { apiKey: "emulator-test-key", projectId: PROJECT_ID },
  `firestore-rules-${Date.now()}`
);
const auth = getAuth(app);
const firestore = getFirestore(app, DATABASE_ID);

connectAuthEmulator(auth, `http://${AUTH_EMULATOR_HOST}`, { disableWarnings: true });
connectFirestoreEmulator(
  firestore,
  FIRESTORE_EMULATOR_HOST.split(":")[0],
  Number(FIRESTORE_EMULATOR_HOST.split(":")[1])
);

function firestoreValue(value) {
  if (typeof value === "string") {
    return { stringValue: value };
  }
  if (typeof value === "number") {
    return { integerValue: value };
  }
  if (typeof value === "boolean") {
    return { booleanValue: value };
  }
  if (Array.isArray(value)) {
    return { arrayValue: { values: value.map(firestoreValue) } };
  }
  if (value instanceof Date) {
    return { timestampValue: value.toISOString() };
  }
  throw new TypeError(`Unsupported Firestore test value: ${typeof value}`);
}

async function seedDocument(collectionName, documentId, value) {
  const url = new URL(
    `http://${FIRESTORE_EMULATOR_HOST}/v1/projects/${PROJECT_ID}/databases/${DATABASE_ID}/documents/${collectionName}/${documentId}`
  );
  const response = await fetch(url, {
    method: "PATCH",
    headers: {
      Authorization: "Bearer owner",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      fields: Object.fromEntries(
        Object.entries(value).map(([field, fieldValue]) => [
          field,
          firestoreValue(fieldValue),
        ])
      ),
    }),
  });
  if (!response.ok) {
    throw new Error(`Firestore emulator test seed failed with HTTP ${response.status}.`);
  }
}

async function assertPermissionDenied(operation) {
  await assert.rejects(operation, (error) => error.code === "permission-denied");
}

test("rules enforce private ownership and public projection on the named database", async () => {
  const { user } = await signInAnonymously(auth);
  const otherUid = "rules-test-other-player";
  const now = new Date();
  await Promise.all([
    seedDocument("users", user.uid, {
      gamerId: "AR-827491",
      username: "arena_one",
      accountStatus: "active",
      wins: 0,
    }),
    seedDocument("privateAccounts", user.uid, {
      uid: user.uid,
      accountStatus: "active",
    }),
    seedDocument("publicPlayers", user.uid, {
      gamerId: "AR-827491",
      username: "arena_one",
      usernameNormalized: "arena_one",
      displayName: "Arena One",
      avatarId: "ember",
      profileVisibility: "public",
      totalMatches: 0,
      wins: 0,
      losses: 0,
      draws: 0,
      experiencePoints: 0,
      playerLevel: 1,
      createdAt: now,
      updatedAt: now,
    }),
    seedDocument("publicPlayers", otherUid, {
      gamerId: "AR-827492",
      username: "arena_two",
      usernameNormalized: "arena_two",
      displayName: "Arena Two",
      avatarId: "tide",
      profileVisibility: "private",
    }),
    seedDocument("publicPlayers", "rules-test-private-field", {
      gamerId: "AR-827493",
      username: "player_secret",
      usernameNormalized: "player_secret",
      displayName: "Player Secret",
      avatarId: "wild",
      profileVisibility: "public",
      email: "private@example.invalid",
    }),
    seedDocument("onlineMatches", "rules-test-match", {
      playerUids: [user.uid, otherUid],
      status: "ACTIVE",
    }),
    seedDocument("onlineMatches/rules-test-match/hands", user.uid, {
      cards: ["private-card-owner"],
    }),
    seedDocument("onlineMatches/rules-test-match/hands", otherUid, {
      cards: ["private-card-other"],
    }),
    seedDocument("onlineMatches/rules-test-match/private", "state", {
      drawPile: ["hidden-card"],
    }),
    seedDocument("privateRooms", "rules-test-room", {
      memberUids: [user.uid, otherUid],
      status: "WAITING",
    }),
    seedDocument(`onlineMatchHistory/${user.uid}/matches`, "rules-test-history", {
      matchId: "rules-test-match",
      verified: true,
    }),
    seedDocument(`friendships/${user.uid}/friends`, otherUid, {
      uid: otherUid,
      displayName: "Friend",
    }),
    seedDocument(`friendRequests/${user.uid}/incoming`, otherUid, {
      requesterUid: otherUid,
      requestId: "rules-request-001",
    }),
    seedDocument(`userBlocks/${user.uid}/users`, otherUid, {
      blockedUid: otherUid,
    }),
    seedDocument(`roomInvitations/${user.uid}/inbox`, "room-invite-001", {
      fromUid: otherUid,
      toUid: user.uid,
      status: "PENDING",
    }),
    seedDocument("conversations", "rules-test-conversation", {
      memberUids: [user.uid, otherUid],
      type: "DIRECT",
    }),
    seedDocument("conversations/rules-test-conversation/messages", "message-001", {
      senderUid: otherUid,
      recipientUid: user.uid,
      text: "Hello",
      status: "SENT",
      createdAt: now,
    }),
    seedDocument("privateRooms/rules-test-room/messages", "room-message-001", {
      senderUid: otherUid,
      text: "Ready",
      createdAt: now,
    }),
    seedDocument("reports", `${user.uid}_rules-report-001`, {
      reporterUid: user.uid,
      reportedUid: otherUid,
      reason: "SPAM",
      status: "NEW",
    }),
    seedDocument("reports", `${otherUid}_rules-report-002`, {
      reporterUid: otherUid,
      reportedUid: user.uid,
      reason: "SPAM",
      status: "NEW",
    }),
    seedDocument(`inAppNotifications/${user.uid}/items`, "rules-notification-001", {
      type: "FRIEND_REQUEST",
      actorUid: otherUid,
      createdAt: now,
    }),
    seedDocument("notificationPreferences", user.uid, { messages: false }),
    seedDocument(`notificationTokens/${user.uid}/devices`, "device-rules-001", {
      token: "secret-fcm-token",
    }),
  ]);

  assert.equal((await getDoc(doc(firestore, "users", user.uid))).exists(), true);
  assert.equal((await getDoc(doc(firestore, "privateAccounts", user.uid))).exists(), true);
  await assertPermissionDenied(getDoc(doc(firestore, "users", otherUid)));
  await assertPermissionDenied(getDoc(doc(firestore, "publicPlayers", otherUid)));
  await assertPermissionDenied(
    getDoc(doc(firestore, "publicPlayers", "rules-test-private-field"))
  );

  await assertPermissionDenied(
    setDoc(doc(firestore, "users", user.uid), { wins: 1000 }, { merge: true })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "publicPlayers", user.uid), { gamerId: "AR-000001" }, { merge: true })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "usernames", "admin"), { uid: user.uid })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "gamerIds", "AR-000001"), { uid: user.uid })
  );
  await assertPermissionDenied(getDoc(doc(firestore, "usernames", "arena_one")));
  await assertPermissionDenied(getDoc(doc(firestore, "wallets", user.uid)));
  await assertPermissionDenied(getDoc(doc(firestore, "dailyRewardClaims", user.uid)));
  await assertPermissionDenied(getDoc(doc(firestore, "missionClaims", user.uid)));
  await assertPermissionDenied(getDoc(doc(firestore, "walletRateLimits", user.uid)));
  await assertPermissionDenied(
    setDoc(doc(firestore, "wallets", user.uid), {
      coinsBalance: 1_000_000,
      totalCoinsEarned: 1_000_000,
      totalCoinsSpent: 0,
    })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "wallets", user.uid, "transactions", "fake-ledger"), {
      amount: 1_000_000,
      balanceAfter: 1_000_000,
      status: "COMMITTED",
    })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "dailyRewards", user.uid), {
      lastClaimDate: "2099-01-01",
      streakDays: 99,
    })
  );
  await assertPermissionDenied(
    setDoc(doc(
      firestore,
      "missions",
      user.uid,
      "days",
      "2099-01-01",
      "items",
      "daily_login"
    ), {
      currentProgress: 1,
      completed: true,
      claimed: false,
    })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "progression", user.uid), {
      experiencePoints: 999_999,
      level: 999,
    })
  );
  await assertPermissionDenied(
    getDoc(doc(firestore, "progression", user.uid, "achievementHistory", "daily_streak_3"))
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "progression", user.uid, "achievementHistory", "forged"), {
      status: "UNLOCKED",
    })
  );
  await assertPermissionDenied(getDoc(doc(firestore, "competitiveProfiles", user.uid)));
  await assertPermissionDenied(
    setDoc(doc(firestore, "competitiveProfiles", user.uid), {
      rating: 99_999,
      wins: 999_999,
      isPublic: true,
    })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "competitiveMatchRecords", "forged-result"), {
      verified: true,
      winnerUid: user.uid,
    })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "seasonLeaderboardEntries", `season_${user.uid}`), {
      seasonId: "season",
      uid: user.uid,
      seasonRating: 99_999,
    })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "tournaments", "forged-tournament"), {
      status: "COMPLETED",
      championUid: user.uid,
    })
  );
  await assertPermissionDenied(
    setDoc(doc(
      firestore,
      "competitiveAchievements",
      user.uid,
      "unlocks",
      "forged-achievement"
    ), { title: "Forged" })
  );
  assert.equal((await getDoc(doc(firestore, "onlineMatches", "rules-test-match"))).exists(), true);
  assert.equal((
    await getDoc(doc(firestore, "onlineMatches", "rules-test-match", "hands", user.uid))
  ).exists(), true);
  assert.equal((await getDoc(doc(firestore, "privateRooms", "rules-test-room"))).exists(), true);
  assert.equal((
    await getDoc(doc(
      firestore,
      "onlineMatchHistory",
      user.uid,
      "matches",
      "rules-test-history"
    ))
  ).exists(), true);
  assert.equal((await getDoc(doc(firestore, "friendships", user.uid, "friends", otherUid))).exists(), true);
  assert.equal((await getDoc(doc(firestore, "friendRequests", user.uid, "incoming", otherUid))).exists(), true);
  assert.equal((await getDoc(doc(firestore, "userBlocks", user.uid, "users", otherUid))).exists(), true);
  assert.equal((await getDoc(doc(
    firestore,
    "roomInvitations",
    user.uid,
    "inbox",
    "room-invite-001"
  ))).exists(), true);
  assert.equal((await getDoc(doc(firestore, "conversations", "rules-test-conversation"))).exists(), true);
  assert.equal((await getDoc(doc(
    firestore,
    "conversations",
    "rules-test-conversation",
    "messages",
    "message-001"
  ))).exists(), true);
  assert.equal((await getDoc(doc(
    firestore,
    "privateRooms",
    "rules-test-room",
    "messages",
    "room-message-001"
  ))).exists(), true);
  assert.equal((await getDoc(doc(
    firestore,
    "reports",
    `${user.uid}_rules-report-001`
  ))).exists(), true);
  assert.equal((await getDoc(doc(
    firestore,
    "inAppNotifications",
    user.uid,
    "items",
    "rules-notification-001"
  ))).exists(), true);
  assert.equal((await getDoc(doc(firestore, "notificationPreferences", user.uid))).exists(), true);
  await assertPermissionDenied(getDoc(doc(
    firestore,
    "notificationTokens",
    user.uid,
    "devices",
    "device-rules-001"
  )));
  await assertPermissionDenied(getDoc(doc(
    firestore,
    "reports",
    `${otherUid}_rules-report-002`
  )));
  await assertPermissionDenied(setDoc(doc(
    firestore,
    "conversations",
    "rules-test-conversation",
    "messages",
    "forged-message"
  ), {
    senderUid: user.uid,
    recipientUid: otherUid,
    text: "Impersonation",
  }));
  await assertPermissionDenied(setDoc(doc(
    firestore,
    "notificationPreferences",
    user.uid
  ), { messages: true }));
  await assertPermissionDenied(setDoc(doc(
    firestore,
    "friendships",
    user.uid,
    "friends",
    "forged-friend"
  ), { uid: "forged-friend" }));
  await assertPermissionDenied(setDoc(doc(firestore, "moderationAudit", "forged-audit"), {
    moderatorUid: user.uid,
  }));
  await assertPermissionDenied(
    getDoc(doc(firestore, "onlineMatches", "rules-test-match", "hands", otherUid))
  );
  await assertPermissionDenied(
    getDoc(doc(firestore, "onlineMatches", "rules-test-match", "private", "state"))
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "onlineMatches", "rules-test-match"), { currentPlayerUid: otherUid })
  );
  await assertPermissionDenied(
    setDoc(doc(firestore, "privateRooms", "rules-test-room"), { ownerUid: otherUid })
  );

  await signOut(auth);
  assert.equal((await getDoc(doc(firestore, "publicPlayers", user.uid))).exists(), true);
  await assertPermissionDenied(getDoc(doc(firestore, "users", user.uid)));
  await assertPermissionDenied(getDoc(doc(firestore, "publicPlayers", otherUid)));
  const { user: outsider } = await signInAnonymously(auth);
  await assertPermissionDenied(getDoc(doc(firestore, "onlineMatches", "rules-test-match")));
  await assertPermissionDenied(
    getDoc(doc(firestore, "onlineMatches", "rules-test-match", "hands", outsider.uid))
  );
  await assertPermissionDenied(getDoc(doc(firestore, "privateRooms", "rules-test-room")));
  await assertPermissionDenied(getDoc(doc(
    firestore,
    "privateRooms",
    "rules-test-room",
    "messages",
    "room-message-001"
  )));
  await assertPermissionDenied(getDoc(doc(
    firestore,
    "conversations",
    "rules-test-conversation"
  )));
  await assertPermissionDenied(getDoc(doc(
    firestore,
    "conversations",
    "rules-test-conversation",
    "messages",
    "message-001"
  )));
  await assertPermissionDenied(
    getDoc(doc(
      firestore,
      "onlineMatchHistory",
      user.uid,
      "matches",
      "rules-test-history"
    ))
  );
  await signOut(auth);
});

after(async () => {
  await deleteApp(app);
});
