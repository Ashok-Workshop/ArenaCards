const test = require("node:test");
const assert = require("node:assert/strict");
if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080") {
  throw new Error("Social tests require the local Firestore Emulator at 127.0.0.1:8080.");
}
const { createFirestoreClient } = require("./firestore-client");
const { createSocialService } = require("./social-service");

const db = createFirestoreClient();
const players = new Map([
  ["social-player-a", "AR-111111"],
  ["social-player-b", "AR-222222"],
  ["social-player-c", "AR-333333"],
]);

function request(uid, data = {}, token = {}) {
  return {
    auth: {
      uid,
      token: {
        firebase: { sign_in_provider: "google.com" },
        ...token,
      },
    },
    data,
  };
}

async function seedPlayer(uid) {
  const gamerId = players.get(uid);
  const profile = {
    uid,
    username: uid,
    usernameNormalized: uid,
    displayName: `Display ${uid}`,
    gamerId,
    avatarId: "ember",
    accountStatus: "active",
    profileVisibility: "public",
  };
  await Promise.all([
    db.collection("users").doc(uid).set(profile),
    db.collection("publicPlayers").doc(uid).set(profile),
    db.collection("gamerIds").doc(gamerId).set({ uid }),
  ]);
}

async function deleteTree(reference) {
  for (const collection of await reference.listCollections()) {
    const snapshot = await collection.get();
    for (const document of snapshot.docs) await deleteTree(document.ref);
  }
  await reference.delete().catch((error) => {
    if (error.code !== 5) throw error;
  });
}

async function cleanup(uids, extra = {}) {
  const topCollections = [
    "friendships",
    "friendRequests",
    "userBlocks",
    "playerPresence",
    "roomInvitations",
    "notificationPreferences",
    "notificationTokens",
    "inAppNotifications",
    "messageRateLimits",
    "reportRateLimits",
  ];
  await Promise.all([
    ...uids.flatMap((uid) => topCollections.map((name) =>
      deleteTree(db.collection(name).doc(uid))
    )),
    ...uids.map((uid) => deleteTree(db.collection("users").doc(uid))),
    ...uids.map((uid) => deleteTree(db.collection("publicPlayers").doc(uid))),
    ...uids.map((uid) => deleteTree(db.collection("gamerIds").doc(players.get(uid)))),
    ...Object.values(extra).map((reference) => deleteTree(reference)),
    ...uids.map((uid) => db.collection("messageRateLimits").doc(uid).delete().catch(() => {})),
    ...uids.flatMap((uid) => [
      db.collection("reports").doc(`${uid}_report-request-001`).delete().catch(() => {}),
      db.collection("reports").doc(`${uid}_report-request-002`).delete().catch(() => {}),
      db.collection("reports").doc(`${uid}_report-request-003`).delete().catch(() => {}),
      db.collection("reports").doc(`${uid}_report-request-004`).delete().catch(() => {}),
      db.collection("reports").doc(`${uid}_report-request-005`).delete().catch(() => {}),
    ]),
  ]);
}

test("Gamer ID friend requests are private, idempotent and relationship changes are symmetric", {
  timeout: 60_000,
}, async () => {
  const uids = [...players.keys()];
  const service = createSocialService(db);
  try {
    await Promise.all(uids.map(seedPlayer));
    assert.equal(
      (await service.searchPlayerByGamerId(request(uids[0], { gamerId: "AR-222222" })))
        .player.uid,
      uids[1]
    );
    await assert.rejects(
      service.sendFriendRequest(request(uids[0], {
        gamerId: "AR-111111",
        requestId: "self-request-001",
      })),
      (error) => error.code === "failed-precondition"
    );

    const outcomes = await Promise.allSettled([
      service.sendFriendRequest(request(uids[0], {
        gamerId: "AR-222222",
        requestId: "friend-request-0001",
      })),
      service.sendFriendRequest(request(uids[0], {
        gamerId: "AR-222222",
        requestId: "friend-request-0002",
      })),
    ]);
    assert.equal(outcomes.filter((result) => result.status === "fulfilled").length, 1);
    const incoming = await service.listFriendRequests(request(uids[1], { direction: "incoming" }));
    assert.equal(incoming.requests.length, 1);
    assert.equal(incoming.requests[0].uid, uids[0]);
    await service.acceptFriendRequest(request(uids[1], {
      requesterUid: uids[0],
      requestId: incoming.requests[0].requestId,
    }));
    await service.acceptFriendRequest(request(uids[1], {
      requesterUid: uids[0],
      requestId: incoming.requests[0].requestId,
    }));
    assert.equal((await service.listFriends(request(uids[0]))).friends.length, 1);
    assert.equal((await service.listFriends(request(uids[1]))).friends.length, 1);
    await service.removeFriend(request(uids[0], { friendUid: uids[1] }));
    assert.equal((await service.listFriends(request(uids[1]))).friends.length, 0);

    await service.sendFriendRequest(request(uids[0], {
      gamerId: "AR-222222",
      requestId: "friend-request-block",
    }));
    await service.blockPlayer(request(uids[1], { playerUid: uids[0] }));
    await assert.rejects(
      service.acceptFriendRequest(request(uids[1], {
        requesterUid: uids[0],
        requestId: "friend-request-block",
      })),
      (error) => error.code === "not-found"
    );
    await assert.rejects(
      service.sendFriendRequest(request(uids[0], {
        gamerId: "AR-222222",
        requestId: "friend-request-after-block",
      })),
      (error) => error.code === "permission-denied"
    );
    assert.equal((await service.listBlockedPlayers(request(uids[1]))).players[0].uid, uids[0]);
    await service.unblockPlayer(request(uids[1], { playerUid: uids[0] }));
    assert.equal((await service.listBlockedPlayers(request(uids[1]))).players.length, 0);
  } finally {
    await cleanup(uids);
  }
});

test("private-room invitations validate friendship and joining, then expire safely", {
  timeout: 60_000,
}, async () => {
  const uids = [...players.keys()];
  let nowMillis = Date.UTC(2026, 8, 1, 12);
  const service = createSocialService(db, { now: () => nowMillis });
  const roomRef = db.collection("privateRooms").doc("social-room-test");
  try {
    await Promise.all(uids.map(seedPlayer));
    await service.sendFriendRequest(request(uids[0], {
      gamerId: "AR-222222",
      requestId: "invite-friend-request",
    }));
    await service.acceptFriendRequest(request(uids[1], {
      requesterUid: uids[0],
      requestId: "invite-friend-request",
    }));
    await roomRef.set({
      roomId: roomRef.id,
      ownerUid: uids[0],
      memberUids: [uids[0]],
      players: [{ uid: uids[0], displayName: "Owner", ready: true }],
      capacity: 2,
      status: "WAITING",
      roomCode: "ABCDEFGH23",
      expiresAtMillis: nowMillis + 60_000,
    });
    const invited = await service.inviteFriendToRoom(request(uids[0], {
      roomId: roomRef.id,
      friendUid: uids[1],
      requestId: "room-invite-0001",
    }));
    assert.equal(invited.sent, true);
    const inbox = await service.listRoomInvitations(request(uids[1]));
    assert.equal(inbox.invitations.length, 1);
    const accepted = await service.acceptRoomInvitation(request(uids[1], {
      invitationId: inbox.invitations[0].invitationId,
    }));
    assert.equal(accepted.accepted, true);
    assert.deepEqual((await roomRef.get()).get("memberUids").sort(), [...uids.slice(0, 2)].sort());

    const expiringRoom = db.collection("privateRooms").doc("social-room-expiring");
    await service.sendFriendRequest(request(uids[0], {
      gamerId: "AR-333333",
      requestId: "invite-expiring-friend-request",
    }));
    await service.acceptFriendRequest(request(uids[2], {
      requesterUid: uids[0],
      requestId: "invite-expiring-friend-request",
    }));
    await expiringRoom.set({
      roomId: expiringRoom.id,
      ownerUid: uids[0],
      memberUids: [uids[0]],
      players: [{ uid: uids[0], displayName: "Owner", ready: true }],
      capacity: 3,
      status: "WAITING",
      roomCode: "ABCDEFGH24",
      expiresAtMillis: nowMillis + 120_000,
    });
    await service.inviteFriendToRoom(request(uids[0], {
      roomId: expiringRoom.id,
      friendUid: uids[2],
      requestId: "room-invite-expire",
    }));
    nowMillis += 16 * 60_000;
    await assert.rejects(
      service.acceptRoomInvitation(request(uids[2], {
        invitationId: `${expiringRoom.id}_${uids[0]}`,
      })),
      (error) => error.code === "deadline-exceeded"
    );
    const expired = await db.collection("roomInvitations").doc(uids[2])
      .collection("inbox").doc(`${expiringRoom.id}_${uids[0]}`).get();
    assert.equal(expired.get("status"), "EXPIRED");
    await assert.rejects(
      service.inviteFriendToRoom(request(uids[0], {
        roomId: expiringRoom.id,
        friendUid: uids[2],
        requestId: "room-invite-expired-room",
      })),
      (error) => error.code === "failed-precondition"
    );
    await cleanup(uids, { roomRef, expiringRoom });
  } finally {
    await cleanup(uids, { roomRef });
  }
});

test("private and room chat enforce membership, blocks, limits, reporting and notification preferences", {
  timeout: 60_000,
}, async () => {
  const uids = [...players.keys()];
  const pushes = [];
  const service = createSocialService(db, {
    sendPush: async (tokens, payload) => {
      pushes.push({ tokens, payload });
      return { invalidTokens: [] };
    },
  });
  const roomRef = db.collection("privateRooms").doc("social-chat-room");
  try {
    await Promise.all(uids.map(seedPlayer));
    await service.sendFriendRequest(request(uids[0], {
      gamerId: "AR-222222",
      requestId: "chat-friend-request",
    }));
    await service.acceptFriendRequest(request(uids[1], {
      requesterUid: uids[0],
      requestId: "chat-friend-request",
    }));
    const { conversationId } = await service.openConversation(request(uids[0], {
      friendUid: uids[1],
    }));
    await service.registerNotificationToken(request(uids[1], {
      token: "fcm-token-that-is-long-enough-for-testing-123456789",
      deviceId: "device-test-0001",
    }));
    await service.setNotificationPreferences(request(uids[1], { messages: false }));
    const sent = await service.sendDirectMessage(request(uids[0], {
      conversationId,
      text: "Hello from ArenaCards.",
      requestId: "message-request-001",
    }));
    const duplicate = await service.sendDirectMessage(request(uids[0], {
      conversationId,
      text: "Hello from ArenaCards.",
      requestId: "message-request-001",
    }));
    assert.equal(sent.duplicate, false);
    assert.equal(duplicate.duplicate, true);
    assert.equal((await service.getDirectMessages(request(uids[1], {
      conversationId,
      pageSize: 10,
    }))).messages.length, 1);
    assert.equal((await service.listConversations(request(uids[1]))).conversations[0].unreadCount, 1);
    await service.markConversationRead(request(uids[1], { conversationId }));
    assert.equal((await service.listConversations(request(uids[1]))).conversations[0].unreadCount, 0);
    assert.equal(pushes.length, 0);
    await assert.rejects(
      service.getDirectMessages(request(uids[2], { conversationId })),
      (error) => error.code === "permission-denied"
    );
    await assert.rejects(
      service.sendDirectMessage(request(uids[0], {
        conversationId,
        text: "x".repeat(1_001),
        requestId: "message-request-long",
      })),
      (error) => error.code === "invalid-argument"
    );
    await service.reportPlayer(request(uids[1], {
      reportedUid: uids[0],
      reason: "SPAM",
      details: "Repeated unsolicited messages",
      messageId: sent.messageId,
      requestId: "report-request-001",
    }));
    assert.equal((await service.listReports(request(uids[1]))).reports.length, 1);
    const duplicateReport = await service.reportPlayer(request(uids[1], {
      reportedUid: uids[0],
      reason: "SPAM",
      details: "Repeated unsolicited messages",
      messageId: sent.messageId,
      requestId: "report-request-001",
    }));
    assert.equal(duplicateReport.submitted, true);
    assert.equal((await service.listReports(request(uids[1]))).reports.length, 1);
    await assert.rejects(
      service.reportPlayer(request(uids[1], {
        reportedUid: uids[1],
        reason: "SPAM",
        requestId: "report-self-0001",
      })),
      (error) => error.code === "invalid-argument"
    );
    await assert.rejects(
      service.reportPlayer(request(uids[1], {
        reportedUid: uids[0],
        reason: "NOT_A_REASON",
        requestId: "report-invalid-01",
      })),
      (error) => error.code === "invalid-argument"
    );
    await assert.rejects(
      service.reviewReport(request(uids[0], {
        reportId: `${uids[1]}_report-request-001`,
        status: "RESOLVED",
      })),
      (error) => error.code === "permission-denied"
    );
    await service.reviewReport(request(uids[2], {
      reportId: `${uids[1]}_report-request-001`,
      status: "UNDER_REVIEW",
      reviewNote: "Queued for moderator review",
    }, { moderator: true }));

    const adminReview = await service.reviewReport(request(uids[0], {
      reportId: `${uids[1]}_report-request-001`,
      status: "RESOLVED",
      reviewNote: "Reviewed by administrator",
    }, { admin: true }));
    assert.equal(adminReview.reviewed, true);
    const reviewedReport = await db.collection("reports")
      .doc(`${uids[1]}_report-request-001`).get();
    assert.equal(reviewedReport.get("status"), "RESOLVED");
    assert.equal(reviewedReport.get("reviewerUid"), uids[0]);

    for (let index = 2; index <= 5; index += 1) {
      await service.reportPlayer(request(uids[1], {
        reportedUid: uids[0],
        reason: "SPAM",
        requestId: "report-request-00" + index,
      }));
    }
    await assert.rejects(
      service.reportPlayer(request(uids[1], {
        reportedUid: uids[0],
        reason: "SPAM",
        requestId: "report-request-006",
      })),
      (error) => error.code === "resource-exhausted"
    );

    await roomRef.set({
      roomId: roomRef.id,
      ownerUid: uids[0],
      memberUids: [uids[0], uids[1]],
      capacity: 2,
      status: "WAITING",
      expiresAtMillis: Date.now() + 60_000,
    });
    const roomMessage = await service.sendRoomMessage(request(uids[0], {
      roomId: roomRef.id,
      text: "Room is ready.",
      requestId: "room-message-request-1",
    }));
    assert.equal(roomMessage.duplicate, false);
    assert.equal((await service.getRoomMessages(request(uids[1], {
      roomId: roomRef.id,
    }))).messages.length, 1);
    const notificationPage = await service.listNotifications(request(uids[1], { pageSize: 1 }));
    assert.equal(notificationPage.notifications.length, 1);
    assert.ok(notificationPage.nextCursor);
    const olderNotifications = await service.listNotifications(request(uids[1], {
      pageSize: 1,
      cursorCreatedAtMillis: notificationPage.nextCursor.cursorCreatedAtMillis,
      cursorId: notificationPage.nextCursor.cursorId,
    }));
    assert.equal(olderNotifications.notifications.length, 1);
    assert.notEqual(
      notificationPage.notifications[0].notificationId,
      olderNotifications.notifications[0].notificationId
    );
    await service.blockPlayer(request(uids[1], { playerUid: uids[0] }));
    await assert.rejects(
      service.sendDirectMessage(request(uids[0], {
        conversationId,
        text: "This should not be sent.",
        requestId: "message-after-block",
      })),
      (error) => error.code === "permission-denied"
    );
    await assert.rejects(
      service.sendRoomMessage(request(uids[0], {
        roomId: roomRef.id,
        text: "This should not be sent in room chat.",
        requestId: "room-message-after-block",
      })),
      (error) => error.code === "permission-denied"
    );
    await cleanup(uids, {
      conversation: db.collection("conversations").doc(conversationId),
      roomRef,
    });
  } finally {
    await cleanup(uids, {
      conversation: db.collection("conversations").doc(
        require("node:crypto").createHash("sha256").update(uids.slice(0, 2).sort().join("\u0000")).digest("hex")
      ),
      roomRef,
    });
  }
});

test("presence expires to offline and notification preferences are owner-scoped", {
  timeout: 60_000,
}, async () => {
  const uids = [...players.keys()];
  let nowMillis = Date.UTC(2026, 8, 1, 12);
  const service = createSocialService(db, { now: () => nowMillis });
  try {
    await Promise.all(uids.map(seedPlayer));
    await service.sendFriendRequest(request(uids[0], {
      gamerId: "AR-222222",
      requestId: "presence-friend-request",
    }));
    await service.acceptFriendRequest(request(uids[1], {
      requesterUid: uids[0],
      requestId: "presence-friend-request",
    }));
    await service.updatePresence(request(uids[1], { foreground: true }));
    assert.equal((await service.listFriends(request(uids[0]))).friends[0].presence, "ONLINE");
    await service.updatePresence(request(uids[1], { foreground: false }));
    assert.equal((await service.listFriends(request(uids[0]))).friends[0].presence, "AWAY");
    nowMillis += 3 * 60_000;
    assert.equal((await service.listFriends(request(uids[0]))).friends[0].presence, "OFFLINE");
    await service.setNotificationPreferences(request(uids[0], {
      friendRequests: false,
      messages: true,
    }));
    assert.equal((await service.getNotificationPreferences(request(uids[0]))).friendRequests, false);
    await assert.rejects(
      service.getNotificationPreferences({ data: {} }),
      (error) => error.code === "unauthenticated"
    );
  } finally {
    await cleanup(uids);
  }
});

test.after(async () => {
  await db.terminate();
});
