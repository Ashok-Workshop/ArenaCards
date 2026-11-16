const { createHash, randomUUID } = require("node:crypto");
const { HttpsError } = require("firebase-functions/v2/https");
const Filter = require("bad-words");
const { FieldPath, FieldValue } = require("./firestore-client");

const FRIEND_PAGE_SIZE_MAX = 50;
const MESSAGE_LIMIT = 1_000;
const MESSAGE_WINDOW_MILLIS = 10_000;
const MESSAGE_WINDOW_LIMIT = 8;
const REPORT_WINDOW_MILLIS = 60 * 60 * 1_000;
const REPORT_WINDOW_LIMIT = 5;
const PRESENCE_TTL_MILLIS = 2 * 60 * 1_000;
const INVITATION_TTL_MILLIS = 15 * 60 * 1_000;
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{8,80}$/;
const REASONS = new Set(["HARASSMENT", "SPAM", "ABUSIVE_CONTENT", "INAPPROPRIATE", "OTHER"]);
const messageFilter = new Filter();

function createSocialService(db, options = {}) {
  const now = options.now || Date.now;
  const sendPush = options.sendPush || (async () => {});

  function requireUid(request) {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in to use social features.");
    if (
      request.auth.token.firebase?.sign_in_provider === "password"
      && request.auth.token.email_verified !== true
    ) {
      throw new HttpsError("permission-denied", "Verify your email before using social features.");
    }
    return uid;
  }

  function validateData(data, allowedKeys) {
    if (!data || typeof data !== "object" || Array.isArray(data)) {
      throw new HttpsError("invalid-argument", "Request data must be an object.");
    }
    if (Object.keys(data).some((key) => !allowedKeys.includes(key))) {
      throw new HttpsError("invalid-argument", "Request contains an unsupported field.");
    }
  }

  function validateRequestId(value) {
    if (typeof value !== "string" || !REQUEST_ID_PATTERN.test(value)) {
      throw new HttpsError("invalid-argument", "A valid request ID is required.");
    }
    return value;
  }

  function validateUid(value) {
    if (typeof value !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(value)) {
      throw new HttpsError("invalid-argument", "A valid player is required.");
    }
    return value;
  }

  function friendRef(uid, otherUid) {
    return db.collection("friendships").doc(uid).collection("friends").doc(otherUid);
  }

  function incomingRef(uid, requesterUid) {
    return db.collection("friendRequests").doc(uid).collection("incoming").doc(requesterUid);
  }

  function outgoingRef(uid, recipientUid) {
    return db.collection("friendRequests").doc(uid).collection("outgoing").doc(recipientUid);
  }

  function blockRef(uid, otherUid) {
    return db.collection("userBlocks").doc(uid).collection("users").doc(otherUid);
  }

  function pairConversationId(firstUid, secondUid) {
    return createHash("sha256")
      .update([firstUid, secondUid].sort().join("\u0000"))
      .digest("hex");
  }

  function requireProfile(profile) {
    if (!profile || profile.accountStatus !== "active") {
      throw new HttpsError("not-found", "This player is unavailable.");
    }
    return profile;
  }

  function publicSummary(profile, uid) {
    return {
      uid,
      displayName: profile.displayName,
      username: profile.username,
      gamerId: profile.gamerId,
      avatarId: profile.avatarId,
    };
  }

  function profileVisible(profile) {
    return profile?.profileVisibility === "public";
  }

  async function notify(uid, category, payload) {
    const pref = await db.collection("notificationPreferences").doc(uid).get();
    if (pref.exists && pref.get(category) === false) return;
    const deviceDocs = await db.collection("notificationTokens").doc(uid)
      .collection("devices").get();
    const tokens = deviceDocs.docs.map((doc) => doc.get("token")).filter(Boolean);
    if (!tokens.length) return;
    let result;
    try {
      result = await sendPush(tokens, {
        ...payload,
        category,
        notificationId: randomUUID(),
      });
    } catch (error) {
      console.error("FCM delivery failed; in-app notification remains available.", {
        uid,
        category,
        error,
      });
      return;
    }
    const invalidTokens = result?.invalidTokens || [];
    if (invalidTokens.length) {
      await Promise.all(deviceDocs.docs
        .filter((doc) => invalidTokens.includes(doc.get("token")))
        .map((doc) => doc.ref.delete()));
    }
  }

  async function recordInAppNotification(uid, type, actorUid, data = {}) {
    const notificationRef = db.collection("inAppNotifications").doc(uid)
      .collection("items").doc(randomUUID());
    await notificationRef.create({
      type,
      actorUid,
      data,
      createdAt: FieldValue.serverTimestamp(),
      readAt: null,
    });
    return notificationRef.id;
  }

  async function searchPlayerByGamerId(request) {
    validateData(request.data, ["gamerId"]);
    requireUid(request);
    const gamerId = typeof request.data.gamerId === "string"
      ? request.data.gamerId.trim().toUpperCase()
      : "";
    if (!/^AR-\d{6}$/.test(gamerId)) {
      throw new HttpsError("invalid-argument", "Enter a valid six-digit Gamer ID.");
    }
    const reservation = await db.collection("gamerIds").doc(gamerId).get();
    if (!reservation.exists) return { player: null };
    const uid = reservation.get("uid");
    const profileSnapshot = await db.collection("users").doc(uid).get();
    const profile = profileSnapshot.data();
    if (!profileVisible(profile) || profile.accountStatus !== "active") return { player: null };
    return { player: publicSummary(profile, uid) };
  }

  async function sendFriendRequest(request) {
    validateData(request.data, ["gamerId", "requestId"]);
    const uid = requireUid(request);
    const requestId = validateRequestId(request.data.requestId);
    const gamerId = typeof request.data.gamerId === "string"
      ? request.data.gamerId.trim().toUpperCase()
      : "";
    if (!/^AR-\d{6}$/.test(gamerId)) {
      throw new HttpsError("invalid-argument", "Enter a valid six-digit Gamer ID.");
    }
    const targetIndex = await db.collection("gamerIds").doc(gamerId).get();
    if (!targetIndex.exists) throw new HttpsError("not-found", "This player is unavailable.");
    const targetUid = validateUid(targetIndex.get("uid"));
    if (targetUid === uid) throw new HttpsError("failed-precondition", "You cannot add yourself.");

    const senderProfileRef = db.collection("users").doc(uid);
    const recipientProfileRef = db.collection("users").doc(targetUid);
    const incoming = incomingRef(targetUid, uid);
    const outgoing = outgoingRef(uid, targetUid);
    const reverseIncoming = incomingRef(uid, targetUid);
    const reverseOutgoing = outgoingRef(targetUid, uid);
    const ownFriend = friendRef(uid, targetUid);
    const otherFriend = friendRef(targetUid, uid);
    const [senderProfile, recipientProfile] = await Promise.all([
      senderProfileRef.get(),
      recipientProfileRef.get(),
    ]);
    const sender = requireProfile(senderProfile.data());
    const recipient = requireProfile(recipientProfile.data());
    if (!profileVisible(recipient)) {
      throw new HttpsError("not-found", "This player is unavailable.");
    }
    const timestamp = FieldValue.serverTimestamp();
    const outcome = await db.runTransaction(async (transaction) => {
      const refs = [
        incoming, outgoing, reverseIncoming, reverseOutgoing,
        ownFriend, otherFriend, blockRef(uid, targetUid), blockRef(targetUid, uid),
      ];
      const snapshots = await Promise.all(refs.map((ref) => transaction.get(ref)));
      const [
        incomingSnapshot, outgoingSnapshot, reverseIncomingSnapshot,
        reverseOutgoingSnapshot, ownFriendSnapshot, otherFriendSnapshot,
        ownBlockSnapshot, otherBlockSnapshot,
      ] = snapshots;
      if (ownBlockSnapshot.exists || otherBlockSnapshot.exists) return { error: "blocked" };
      if (ownFriendSnapshot.exists || otherFriendSnapshot.exists) return { error: "already-friends" };
      if (incomingSnapshot.exists) return { error: "already-sent" };
      if (reverseIncomingSnapshot.exists || reverseOutgoingSnapshot.exists) {
        return { error: "incoming-request" };
      }
      if (outgoingSnapshot.exists) return { error: "already-sent" };
      const requestData = {
        requesterUid: uid,
        recipientUid: targetUid,
        requestId,
        createdAt: timestamp,
      };
      transaction.create(incoming, requestData);
      transaction.create(outgoing, requestData);
      return { sent: true };
    });
    if (outcome.error === "blocked") {
      throw new HttpsError("permission-denied", "A block prevents this friend request.");
    }
    if (outcome.error === "already-friends") {
      throw new HttpsError("already-exists", "You are already friends.");
    }
    if (outcome.error === "incoming-request") {
      throw new HttpsError("failed-precondition", "This player has already requested to be your friend.");
    }
    if (outcome.error === "already-sent") {
      throw new HttpsError("already-exists", "A friend request is already pending.");
    }
    const notificationId = await recordInAppNotification(targetUid, "FRIEND_REQUEST", uid);
    await notify(targetUid, "friendRequests", {
      type: "FRIEND_REQUEST",
      actorUid: uid,
      notificationId,
    });
    return { sent: true };
  }

  async function decideFriendRequest(request, accept) {
    validateData(request.data, ["requesterUid", "requestId"]);
    const uid = requireUid(request);
    const requesterUid = validateUid(request.data.requesterUid);
    const requestId = validateRequestId(request.data.requestId);
    if (requesterUid === uid) throw new HttpsError("invalid-argument", "A request from yourself is invalid.");
    const incoming = incomingRef(uid, requesterUid);
    const outgoing = outgoingRef(requesterUid, uid);
    const ownFriend = friendRef(uid, requesterUid);
    const otherFriend = friendRef(requesterUid, uid);
    const actorProfileRef = db.collection("users").doc(uid);
    const requesterProfileRef = db.collection("users").doc(requesterUid);
    const timestamp = FieldValue.serverTimestamp();
    const result = await db.runTransaction(async (transaction) => {
      const refs = [
        incoming, outgoing, ownFriend, otherFriend,
        actorProfileRef, requesterProfileRef,
        blockRef(uid, requesterUid), blockRef(requesterUid, uid),
      ];
      const snapshots = await Promise.all(refs.map((ref) => transaction.get(ref)));
      const [
        incomingSnapshot, outgoingSnapshot, ownFriendSnapshot, otherFriendSnapshot,
        actorProfile, requesterProfile, ownBlock, otherBlock,
      ] = snapshots;
      if (!incomingSnapshot.exists || incomingSnapshot.get("requestId") !== requestId) {
        if (accept && ownFriendSnapshot.exists && otherFriendSnapshot.exists) {
          return { accepted: true, alreadyApplied: true };
        }
        throw new HttpsError("not-found", "This friend request is no longer available.");
      }
      transaction.delete(incoming);
      if (outgoingSnapshot.exists) transaction.delete(outgoing);
      if (accept && !ownBlock.exists && !otherBlock.exists) {
        const firstProfile = requireProfile(actorProfile.data());
        const secondProfile = requireProfile(requesterProfile.data());
        const firstSummary = publicSummary(firstProfile, uid);
        const secondSummary = publicSummary(secondProfile, requesterUid);
        transaction.create(ownFriend, {
          ...secondSummary,
          createdAt: timestamp,
        });
        transaction.create(otherFriend, {
          ...firstSummary,
          createdAt: timestamp,
        });
        return { accepted: true };
      }
      return { accepted: false };
    });
    if (!accept) return { rejected: true };
    if (!result.accepted) {
      throw new HttpsError("permission-denied", "A block prevents accepting this request.");
    }
    if (!result.alreadyApplied) {
      const notificationId = await recordInAppNotification(
        requesterUid,
        "FRIEND_REQUEST_ACCEPTED",
        uid
      );
      await notify(requesterUid, "friendRequests", {
        type: "FRIEND_REQUEST_ACCEPTED",
        actorUid: uid,
        notificationId,
      });
    }
    return { accepted: true };
  }

  async function acceptFriendRequest(request) {
    return decideFriendRequest(request, true);
  }

  async function rejectFriendRequest(request) {
    return decideFriendRequest(request, false);
  }

  async function cancelFriendRequest(request) {
    validateData(request.data, ["recipientUid", "requestId"]);
    const uid = requireUid(request);
    const recipientUid = validateUid(request.data.recipientUid);
    const requestId = validateRequestId(request.data.requestId);
    const incoming = incomingRef(recipientUid, uid);
    const outgoing = outgoingRef(uid, recipientUid);
    await db.runTransaction(async (transaction) => {
      const [incomingSnapshot, outgoingSnapshot] = await Promise.all([
        transaction.get(incoming),
        transaction.get(outgoing),
      ]);
      if (!outgoingSnapshot.exists || outgoingSnapshot.get("requestId") !== requestId) {
        throw new HttpsError("not-found", "This outgoing request is no longer available.");
      }
      transaction.delete(outgoing);
      if (incomingSnapshot.exists) transaction.delete(incoming);
    });
    return { cancelled: true };
  }

  async function listFriendRequests(request) {
    validateData(request.data || {}, ["direction", "pageSize", "cursorUid"]);
    const uid = requireUid(request);
    const direction = request.data.direction;
    if (!["incoming", "outgoing"].includes(direction)) {
      throw new HttpsError("invalid-argument", "Choose incoming or outgoing requests.");
    }
    const pageSize = request.data.pageSize ?? 20;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > FRIEND_PAGE_SIZE_MAX) {
      throw new HttpsError("invalid-argument", `pageSize must be 1-${FRIEND_PAGE_SIZE_MAX}.`);
    }
    const collection = db.collection("friendRequests").doc(uid).collection(direction);
    let query = collection.orderBy(FieldPath.documentId()).limit(pageSize + 1);
    if (request.data.cursorUid) query = query.startAfter(validateUid(request.data.cursorUid));
    const snapshot = await query.get();
    const docs = snapshot.docs.slice(0, pageSize);
    const profiles = await Promise.all(docs.map((doc) =>
      db.collection("users").doc(direction === "incoming" ? doc.id : doc.get("recipientUid")).get()
    ));
    return {
      requests: docs.map((doc, index) => {
        const profile = profiles[index].data() || {};
        const otherUid = direction === "incoming" ? doc.id : doc.get("recipientUid");
        return {
          uid: otherUid,
          displayName: profile.displayName || "Arena player",
          username: profile.username || "",
          gamerId: profile.gamerId || "",
          avatarId: profile.avatarId || "ember",
          requestId: doc.get("requestId"),
          createdAtMillis: doc.get("createdAt")?.toMillis?.() ?? null,
        };
      }),
      nextCursorUid: snapshot.docs.length > pageSize ? docs[docs.length - 1].id : null,
    };
  }

  async function listFriends(request) {
    validateData(request.data || {}, ["pageSize", "cursorUid"]);
    const uid = requireUid(request);
    const pageSize = request.data.pageSize ?? 20;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > FRIEND_PAGE_SIZE_MAX) {
      throw new HttpsError("invalid-argument", `pageSize must be 1-${FRIEND_PAGE_SIZE_MAX}.`);
    }
    const friends = db.collection("friendships").doc(uid).collection("friends");
    let query = friends.orderBy(FieldPath.documentId()).limit(pageSize + 1);
    if (request.data.cursorUid) query = query.startAfter(validateUid(request.data.cursorUid));
    const snapshot = await query.get();
    const docs = snapshot.docs.slice(0, pageSize);
    const players = await Promise.all(docs.map(async (doc) => {
      const profile = await db.collection("publicPlayers").doc(doc.id).get();
      const presence = await getPresenceForFriend(uid, doc.id);
      return {
        uid: doc.id,
        player: profile.exists && profileVisible(profile.data())
          ? publicSummary(profile.data(), doc.id)
          : { uid: doc.id, displayName: "Private player", username: "", gamerId: "", avatarId: "ember" },
        presence: presence.status,
        lastActiveAtMillis: presence.lastActiveAtMillis,
      };
    }));
    return {
      friends: players,
      nextCursorUid: snapshot.docs.length > pageSize ? docs[docs.length - 1].id : null,
    };
  }

  async function listConversations(request) {
    validateData(request.data || {}, ["pageSize", "cursorCreatedAtMillis", "cursorId"]);
    const uid = requireUid(request);
    const pageSize = request.data.pageSize ?? 20;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > FRIEND_PAGE_SIZE_MAX) {
      throw new HttpsError("invalid-argument", `pageSize must be 1-${FRIEND_PAGE_SIZE_MAX}.`);
    }
    let query = db.collection("conversations")
      .where("memberUids", "array-contains", uid)
      .orderBy("lastMessageAt", "desc")
      .orderBy(FieldPath.documentId(), "desc")
      .limit(pageSize + 1);
    if (request.data.cursorCreatedAtMillis !== undefined || request.data.cursorId !== undefined) {
      if (!Number.isSafeInteger(request.data.cursorCreatedAtMillis)
        || typeof request.data.cursorId !== "string") {
        throw new HttpsError("invalid-argument", "Conversation page cursor is invalid.");
      }
      query = query.startAfter(
        new Date(request.data.cursorCreatedAtMillis),
        request.data.cursorId
      );
    }
    const snapshot = await query.get();
    const hasMore = snapshot.docs.length > pageSize;
    const docs = snapshot.docs.slice(0, pageSize);
    const conversations = await Promise.all(docs.map(async (doc) => {
      const friendUid = doc.get("memberUids").find((memberUid) => memberUid !== uid);
      const [profile, ownBlock, otherBlock] = await Promise.all([
        db.collection("publicPlayers").doc(friendUid).get(),
        blockRef(uid, friendUid).get(),
        blockRef(friendUid, uid).get(),
      ]);
      return {
        conversationId: doc.id,
        friendUid,
        player: profile.exists && profileVisible(profile.data())
          ? publicSummary(profile.data(), friendUid)
          : {
            uid: friendUid,
            displayName: "Private player",
            username: "",
            gamerId: "",
            avatarId: "ember",
          },
        lastMessagePreview: doc.get("lastMessagePreview") || "",
        lastMessageAtMillis: doc.get("lastMessageAt")?.toMillis?.() ?? null,
        unreadCount: doc.get(`unreadCounts.${uid}`) || 0,
        blocked: ownBlock.exists || otherBlock.exists,
      };
    }));
    return {
      conversations,
      nextCursor: hasMore
        ? {
          cursorCreatedAtMillis: docs[docs.length - 1].get("lastMessageAt").toMillis(),
          cursorId: docs[docs.length - 1].id,
        }
        : null,
    };
  }

  async function removeFriend(request) {
    validateData(request.data, ["friendUid"]);
    const uid = requireUid(request);
    const otherUid = validateUid(request.data.friendUid);
    if (otherUid === uid) throw new HttpsError("invalid-argument", "A player cannot remove themselves.");
    const first = friendRef(uid, otherUid);
    const second = friendRef(otherUid, uid);
    await db.runTransaction(async (transaction) => {
      const [firstSnapshot, secondSnapshot] = await Promise.all([
        transaction.get(first),
        transaction.get(second),
      ]);
      if (!firstSnapshot.exists && !secondSnapshot.exists) {
        throw new HttpsError("not-found", "This friend relationship no longer exists.");
      }
      transaction.delete(first);
      transaction.delete(second);
    });
    return { removed: true };
  }

  async function blockPlayer(request) {
    validateData(request.data, ["playerUid"]);
    const uid = requireUid(request);
    const otherUid = validateUid(request.data.playerUid);
    if (uid === otherUid) throw new HttpsError("invalid-argument", "You cannot block yourself.");
    const targetProfile = await db.collection("users").doc(otherUid).get();
    requireProfile(targetProfile.data());
    const ownBlock = blockRef(uid, otherUid);
    const ownFriend = friendRef(uid, otherUid);
    const otherFriend = friendRef(otherUid, uid);
    const incoming = incomingRef(uid, otherUid);
    const outgoing = outgoingRef(uid, otherUid);
    const reverseIncoming = incomingRef(otherUid, uid);
    const reverseOutgoing = outgoingRef(otherUid, uid);
    await db.runTransaction(async (transaction) => {
      const refs = [
        ownBlock, ownFriend, otherFriend, incoming, outgoing, reverseIncoming, reverseOutgoing,
      ];
      const snapshots = await Promise.all(refs.map((ref) => transaction.get(ref)));
      if (!snapshots[0].exists) {
        transaction.create(ownBlock, { blockedUid: otherUid, createdAt: FieldValue.serverTimestamp() });
      }
      for (let index = 1; index < refs.length; index += 1) {
        if (snapshots[index].exists) transaction.delete(refs[index]);
      }
    });
    return { blocked: true };
  }

  async function unblockPlayer(request) {
    validateData(request.data, ["playerUid"]);
    const uid = requireUid(request);
    const otherUid = validateUid(request.data.playerUid);
    const ref = blockRef(uid, otherUid);
    const snapshot = await ref.get();
    if (!snapshot.exists) return { unblocked: true };
    await ref.delete();
    return { unblocked: true };
  }

  async function listBlockedPlayers(request) {
    validateData(request.data || {}, ["pageSize", "cursorUid"]);
    const uid = requireUid(request);
    const pageSize = request.data.pageSize ?? 20;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > FRIEND_PAGE_SIZE_MAX) {
      throw new HttpsError("invalid-argument", `pageSize must be 1-${FRIEND_PAGE_SIZE_MAX}.`);
    }
    const collection = db.collection("userBlocks").doc(uid).collection("users");
    let query = collection.orderBy(FieldPath.documentId()).limit(pageSize + 1);
    if (request.data.cursorUid) query = query.startAfter(validateUid(request.data.cursorUid));
    const snapshot = await query.get();
    const docs = snapshot.docs.slice(0, pageSize);
    const players = await Promise.all(docs.map(async (doc) => {
      const profile = await db.collection("users").doc(doc.id).get();
      return {
        uid: doc.id,
        displayName: profile.get("displayName") || "Arena player",
        username: profile.get("username") || "",
        gamerId: profile.get("gamerId") || "",
        avatarId: profile.get("avatarId") || "ember",
        createdAtMillis: doc.get("createdAt")?.toMillis?.() ?? null,
      };
    }));
    return {
      players,
      nextCursorUid: snapshot.docs.length > pageSize ? docs[docs.length - 1].id : null,
    };
  }

  async function getPresenceForFriend(uid, friendUid) {
    const [presence, playerStatus] = await Promise.all([
      db.collection("playerPresence").doc(friendUid).get(),
      db.collection("onlinePlayers").doc(friendUid).get(),
    ]);
    const data = presence.data() || {};
    const lastActiveAtMillis = data.lastActiveAtMillis || null;
    if (!presence.exists || now() - (lastActiveAtMillis || 0) > PRESENCE_TTL_MILLIS) {
      return { status: "OFFLINE", lastActiveAtMillis };
    }
    if (playerStatus.exists && playerStatus.get("status") === "IN_MATCH") {
      return { status: "IN_MATCH", lastActiveAtMillis };
    }
    return {
      status: data.foreground === true ? "ONLINE" : "AWAY",
      lastActiveAtMillis,
    };
  }

  async function updatePresence(request) {
    validateData(request.data, ["foreground"]);
    const uid = requireUid(request);
    if (typeof request.data.foreground !== "boolean") {
      throw new HttpsError("invalid-argument", "Foreground state must be true or false.");
    }
    const timestamp = now();
    await db.collection("playerPresence").doc(uid).set({
      foreground: request.data.foreground,
      lastActiveAtMillis: timestamp,
      expiresAt: new Date(timestamp + PRESENCE_TTL_MILLIS),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { updated: true, expiresAtMillis: timestamp + PRESENCE_TTL_MILLIS };
  }

  async function expireRoomInvitations(options = {}) {
    const limit = options.limit ?? 100;
    if (!Number.isInteger(limit) || limit < 1 || limit > 500) {
      throw new HttpsError("invalid-argument", "Invitation expiry batch size must be 1-500.");
    }
    const cutoff = now();
    const pending = await db.collectionGroup("inbox")
      .where("status", "==", "PENDING")
      .where("expiresAtMillis", "<=", cutoff)
      .orderBy("expiresAtMillis", "asc")
      .limit(limit)
      .get();
    const results = await Promise.allSettled(pending.docs.map((snapshot) =>
      db.runTransaction(async (transaction) => {
        const current = await transaction.get(snapshot.ref);
        if (!current.exists || current.get("status") !== "PENDING"
          || current.get("expiresAtMillis") > now()) return false;
        const senderUid = current.get("fromUid");
        const outbox = db.collection("roomInvitations").doc(senderUid)
          .collection("outbox").doc(snapshot.id);
        transaction.set(snapshot.ref, {
          status: "EXPIRED",
          resolvedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
        transaction.set(outbox, {
          status: "EXPIRED",
          resolvedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
        return true;
      })
    ));
    const failed = results.filter((result) => result.status === "rejected");
    if (failed.length) {
      throw new AggregateError(
        failed.map((result) => result.reason),
        `${failed.length} room invitation(s) could not be expired.`
      );
    }
    return {
      scanned: pending.size,
      expired: results.filter((result) => result.status === "fulfilled" && result.value).length,
    };
  }

  async function listRoomInvitations(request) {
    validateData(request.data || {}, ["pageSize", "cursorId"]);
    const uid = requireUid(request);
    const pageSize = request.data.pageSize ?? 20;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > FRIEND_PAGE_SIZE_MAX) {
      throw new HttpsError("invalid-argument", `pageSize must be 1-${FRIEND_PAGE_SIZE_MAX}.`);
    }
    const collection = db.collection("roomInvitations").doc(uid).collection("inbox");
    let query = collection.where("status", "==", "PENDING")
      .orderBy(FieldPath.documentId()).limit(pageSize + 1);
    if (request.data.cursorId) query = query.startAfter(validateRequestId(request.data.cursorId));
    const snapshot = await query.get();
    const docs = snapshot.docs.slice(0, pageSize);
    const invitations = await Promise.all(docs.map(async (doc) => {
      const inviter = await db.collection("publicPlayers").doc(doc.get("fromUid")).get();
      return {
        invitationId: doc.id,
        roomId: doc.get("roomId"),
        roomCode: doc.get("roomCode"),
        fromUid: doc.get("fromUid"),
        inviter: inviter.exists ? publicSummary(inviter.data(), inviter.id) : null,
        expiresAtMillis: doc.get("expiresAtMillis"),
        createdAtMillis: doc.get("createdAtMillis"),
      };
    }));
    return {
      invitations,
      nextCursorId: snapshot.docs.length > pageSize ? docs[docs.length - 1].id : null,
    };
  }

  async function inviteFriendToRoom(request) {
    validateData(request.data, ["roomId", "friendUid", "requestId"]);
    const uid = requireUid(request);
    const roomId = validateUid(request.data.roomId);
    const friendUid = validateUid(request.data.friendUid);
    const requestId = validateRequestId(request.data.requestId);
    if (friendUid === uid) throw new HttpsError("invalid-argument", "You cannot invite yourself.");
    const invitationId = `${roomId}_${uid}`;
    const inbox = db.collection("roomInvitations").doc(friendUid)
      .collection("inbox").doc(invitationId);
    const outbox = db.collection("roomInvitations").doc(uid)
      .collection("outbox").doc(invitationId);
    const roomRef = db.collection("privateRooms").doc(roomId);
    const senderFriend = friendRef(uid, friendUid);
    const receiverFriend = friendRef(friendUid, uid);
    const timestamp = now();
    const outcome = await db.runTransaction(async (transaction) => {
      const refs = [
        roomRef, inbox, outbox, senderFriend, receiverFriend,
        blockRef(uid, friendUid), blockRef(friendUid, uid),
        db.collection("onlinePlayers").doc(friendUid),
      ];
      const snapshots = await Promise.all(refs.map((ref) => transaction.get(ref)));
      const [room, oldInbox, oldOutbox, firstFriend, secondFriend, firstBlock, secondBlock, active] =
        snapshots;
      if (!room.exists || room.get("ownerUid") !== uid || !room.get("memberUids").includes(uid)) {
        return { error: "room" };
      }
      if (room.get("status") !== "WAITING" || room.get("expiresAtMillis") <= timestamp
        || room.get("memberUids").length >= room.get("capacity")) return { error: "room" };
      if (firstBlock.exists || secondBlock.exists || !firstFriend.exists || !secondFriend.exists) {
        return { error: "relationship" };
      }
      if (active.exists && ["IN_ROOM", "IN_MATCH", "QUEUED"].includes(active.get("status"))) {
        return { error: "busy" };
      }
      if (oldInbox.exists && oldInbox.get("status") === "PENDING"
        && oldInbox.get("expiresAtMillis") > timestamp) return { alreadySent: true };
      const invitation = {
        invitationId,
        roomId,
        roomCode: room.get("roomCode"),
        fromUid: uid,
        toUid: friendUid,
        requestId,
        status: "PENDING",
        createdAtMillis: timestamp,
        expiresAtMillis: Math.min(timestamp + INVITATION_TTL_MILLIS, room.get("expiresAtMillis")),
        createdAt: FieldValue.serverTimestamp(),
      };
      transaction.set(inbox, invitation);
      transaction.set(outbox, invitation);
      return { sent: true };
    });
    if (outcome.error) {
      throw new HttpsError("failed-precondition", "The room or friend is unavailable for invitation.");
    }
    if (outcome.alreadySent) return { sent: true, duplicate: true };
    const notificationId = await recordInAppNotification(
      friendUid,
      "ROOM_INVITATION",
      uid,
      { invitationId, roomId }
    );
    await notify(friendUid, "roomInvitations", {
      type: "ROOM_INVITATION",
      actorUid: uid,
      notificationId,
      invitationId,
    });
    return { sent: true, invitationId };
  }

  async function decideRoomInvitation(request, decision) {
    validateData(request.data, ["invitationId"]);
    const uid = requireUid(request);
    const invitationId = validateRequestId(request.data.invitationId);
    const inbox = db.collection("roomInvitations").doc(uid).collection("inbox").doc(invitationId);
    const outcome = await db.runTransaction(async (transaction) => {
      const invitation = await transaction.get(inbox);
      if (!invitation.exists) throw new HttpsError("not-found", "This invitation is unavailable.");
      if (invitation.get("status") !== "PENDING") {
        if (decision === "ACCEPTED" && invitation.get("status") === "ACCEPTED") {
          return { roomId: invitation.get("roomId"), alreadyApplied: true };
        }
        throw new HttpsError("failed-precondition", "This invitation has already been resolved.");
      }
      if (invitation.get("expiresAtMillis") <= now()) {
        transaction.set(inbox, { status: "EXPIRED", resolvedAt: FieldValue.serverTimestamp() }, { merge: true });
        return { error: "expired" };
      }
      const senderUid = invitation.get("fromUid");
      const outbox = db.collection("roomInvitations").doc(senderUid)
        .collection("outbox").doc(invitationId);
      if (decision !== "ACCEPTED") {
        transaction.set(inbox, { status: decision, resolvedAt: FieldValue.serverTimestamp() }, { merge: true });
        transaction.set(outbox, { status: decision, resolvedAt: FieldValue.serverTimestamp() }, { merge: true });
        return { rejected: true };
      }
      const roomRef = db.collection("privateRooms").doc(invitation.get("roomId"));
      const activeRef = db.collection("onlinePlayers").doc(uid);
      const profileRef = db.collection("users").doc(uid);
      const refs = [
        roomRef, activeRef, profileRef,
        friendRef(uid, senderUid), friendRef(senderUid, uid),
        blockRef(uid, senderUid), blockRef(senderUid, uid),
      ];
      const snapshots = await Promise.all(refs.map((ref) => transaction.get(ref)));
      const [room, active, profile, firstFriend, secondFriend, firstBlock, secondBlock] = snapshots;
      if (!room.exists || room.get("status") !== "WAITING"
        || room.get("expiresAtMillis") <= now()
        || room.get("memberUids").length >= room.get("capacity")
        || !room.get("memberUids").includes(senderUid)
        || !firstFriend.exists || !secondFriend.exists || firstBlock.exists || secondBlock.exists) {
        return { error: "room" };
      }
      if (active.exists && ["IN_ROOM", "IN_MATCH", "QUEUED"].includes(active.get("status"))) {
        return { error: "busy" };
      }
      const player = requireProfile(profile.data());
      const roomPlayer = {
        uid,
        displayName: player.displayName,
        username: player.username,
        gamerId: player.gamerId,
        avatarId: player.avatarId,
        ready: false,
      };
      transaction.update(roomRef, {
        memberUids: [...room.get("memberUids"), uid],
        players: [...room.get("players"), roomPlayer],
        updatedAt: FieldValue.serverTimestamp(),
      });
      transaction.set(activeRef, {
        status: "IN_ROOM",
        roomId: invitation.get("roomId"),
        activeMatchId: null,
        updatedAt: FieldValue.serverTimestamp(),
      });
      transaction.set(inbox, { status: "ACCEPTED", resolvedAt: FieldValue.serverTimestamp() }, { merge: true });
      transaction.set(outbox, { status: "ACCEPTED", resolvedAt: FieldValue.serverTimestamp() }, { merge: true });
      return { roomId: invitation.get("roomId") };
    });
    if (outcome.error === "expired") {
      throw new HttpsError("deadline-exceeded", "This room invitation has expired.");
    }
    if (outcome.error) {
      throw new HttpsError("failed-precondition", "The room is no longer available to join.");
    }
    if (outcome.roomId && !outcome.alreadyApplied) {
      await recordInAppNotification(
        (await inbox.get()).get("fromUid"),
        "ROOM_INVITATION_ACCEPTED",
        uid,
        { invitationId, roomId: outcome.roomId }
      );
    }
    return outcome.roomId ? { accepted: true, roomId: outcome.roomId } : { rejected: true };
  }

  async function acceptRoomInvitation(request) {
    return decideRoomInvitation(request, "ACCEPTED");
  }

  async function rejectRoomInvitation(request) {
    return decideRoomInvitation(request, "REJECTED");
  }

  async function cancelRoomInvitation(request) {
    validateData(request.data, ["invitationId"]);
    const uid = requireUid(request);
    const invitationId = validateRequestId(request.data.invitationId);
    const outbox = db.collection("roomInvitations").doc(uid).collection("outbox").doc(invitationId);
    await db.runTransaction(async (transaction) => {
      const invitation = await transaction.get(outbox);
      if (!invitation.exists || invitation.get("fromUid") !== uid) {
        throw new HttpsError("not-found", "This outgoing invitation is unavailable.");
      }
      if (invitation.get("status") !== "PENDING") {
        throw new HttpsError("failed-precondition", "This invitation is already resolved.");
      }
      const inbox = db.collection("roomInvitations").doc(invitation.get("toUid"))
        .collection("inbox").doc(invitationId);
      transaction.set(outbox, { status: "CANCELLED", resolvedAt: FieldValue.serverTimestamp() }, { merge: true });
      transaction.set(inbox, { status: "CANCELLED", resolvedAt: FieldValue.serverTimestamp() }, { merge: true });
    });
    return { cancelled: true };
  }

  async function openConversation(request) {
    validateData(request.data, ["friendUid"]);
    const uid = requireUid(request);
    const otherUid = validateUid(request.data.friendUid);
    if (uid === otherUid) throw new HttpsError("invalid-argument", "A conversation needs another player.");
    const ref = friendRef(uid, otherUid);
    const reverse = friendRef(otherUid, uid);
    const [ownFriend, otherFriend, ownBlock, otherBlock] = await Promise.all([
      ref.get(), reverse.get(), blockRef(uid, otherUid).get(), blockRef(otherUid, uid).get(),
    ]);
    if (!ownFriend.exists || !otherFriend.exists || ownBlock.exists || otherBlock.exists) {
      throw new HttpsError("permission-denied", "A conversation requires an unblocked friend relationship.");
    }
    const conversationId = pairConversationId(uid, otherUid);
    const conversation = db.collection("conversations").doc(conversationId);
    await conversation.set({
      memberUids: [uid, otherUid].sort(),
      type: "DIRECT",
      createdAt: FieldValue.serverTimestamp(),
      lastMessageAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { conversationId };
  }

  async function sendDirectMessage(request) {
    validateData(request.data, ["conversationId", "text", "requestId"]);
    const uid = requireUid(request);
    const conversationId = validateRequestId(request.data.conversationId);
    const requestId = validateRequestId(request.data.requestId);
    const text = validateMessageText(request.data.text);
    const conversation = db.collection("conversations").doc(conversationId);
    const message = conversation.collection("messages").doc(`${uid}_${requestId}`);
    const rateRef = db.collection("messageRateLimits").doc(uid);
    const timestamp = now();
    const result = await db.runTransaction(async (transaction) => {
      const conversationSnapshot = await transaction.get(conversation);
      if (!conversationSnapshot.exists || !conversationSnapshot.get("memberUids").includes(uid)) {
        throw new HttpsError("permission-denied", "You are not a member of this conversation.");
      }
      const [messageSnapshot, rate, ...relationshipSnapshots] = await Promise.all([
        transaction.get(message),
        transaction.get(rateRef),
        ...conversationSnapshot.get("memberUids").filter((memberUid) => memberUid !== uid)
          .flatMap((memberUid) => [transaction.get(blockRef(uid, memberUid)), transaction.get(blockRef(memberUid, uid))]),
      ]);
      if (messageSnapshot.exists) return { messageId: message.id, duplicate: true };
      if (relationshipSnapshots.some((snapshot) => snapshot.exists)) {
        throw new HttpsError("permission-denied", "Messaging is unavailable because of a block.");
      }
      assertMessageRate(rate, timestamp, text);
      const recipientUid = conversationSnapshot.get("memberUids").find((memberUid) => memberUid !== uid);
      const payload = {
        senderUid: uid,
        recipientUid,
        text,
        status: "SENT",
        createdAt: FieldValue.serverTimestamp(),
        requestId,
      };
      transaction.create(message, payload);
      transaction.set(conversation, {
        lastMessageAt: FieldValue.serverTimestamp(),
        lastMessagePreview: text.slice(0, 80),
        lastSenderUid: uid,
        unreadCounts: { [recipientUid]: FieldValue.increment(1) },
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      updateMessageRate(transaction, rateRef, rate, timestamp, text);
      return { messageId: message.id, recipientUid, text };
    });
    if (!result.duplicate) {
      const notificationId = await recordInAppNotification(
        result.recipientUid,
        "PRIVATE_MESSAGE",
        uid,
        { conversationId }
      );
      await notify(result.recipientUid, "messages", {
        type: "PRIVATE_MESSAGE",
        actorUid: uid,
        notificationId,
        conversationId,
      });
    }
    return { messageId: result.messageId, duplicate: result.duplicate === true };
  }

  function validateMessageText(value) {
    if (typeof value !== "string") throw new HttpsError("invalid-argument", "Message must be text.");
    const text = value.trim();
    if (!text || text.length > MESSAGE_LIMIT || /[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(text)) {
      throw new HttpsError("invalid-argument", `Message must contain 1-${MESSAGE_LIMIT} supported characters.`);
    }
    if (messageFilter.isProfane(text)) {
      throw new HttpsError("invalid-argument", "Please revise this message before sending.");
    }
    return text;
  }

  function assertMessageRate(rateSnapshot, timestamp, text) {
    const rate = rateSnapshot.exists ? rateSnapshot.data() : {};
    const sameWindow = timestamp - (rate.windowStartedAtMillis || 0) < MESSAGE_WINDOW_MILLIS;
    if (sameWindow && rate.count >= MESSAGE_WINDOW_LIMIT) {
      throw new HttpsError("resource-exhausted", "You are sending messages too quickly.");
    }
    const digest = createHash("sha256").update(text.toLowerCase()).digest("hex");
    if (sameWindow && rate.lastMessageHash === digest) {
      throw new HttpsError("resource-exhausted", "Please wait before repeating the same message.");
    }
  }

  function updateMessageRate(transaction, rateRef, rateSnapshot, timestamp, text) {
    const previous = rateSnapshot.exists ? rateSnapshot.data() : {};
    const sameWindow = timestamp - (previous.windowStartedAtMillis || 0) < MESSAGE_WINDOW_MILLIS;
    transaction.set(rateRef, {
      windowStartedAtMillis: sameWindow ? previous.windowStartedAtMillis : timestamp,
      count: sameWindow ? previous.count + 1 : 1,
      lastMessageHash: createHash("sha256").update(text.toLowerCase()).digest("hex"),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  async function sendRoomMessage(request) {
    validateData(request.data, ["roomId", "text", "requestId"]);
    const uid = requireUid(request);
    const roomId = validateUid(request.data.roomId);
    const requestId = validateRequestId(request.data.requestId);
    const text = validateMessageText(request.data.text);
    const roomRef = db.collection("privateRooms").doc(roomId);
    const message = roomRef.collection("messages").doc(`${uid}_${requestId}`);
    const rateRef = db.collection("messageRateLimits").doc(uid);
    const timestamp = now();
    const result = await db.runTransaction(async (transaction) => {
      const room = await transaction.get(roomRef);
      if (!room.exists || !room.get("memberUids").includes(uid)
        || room.get("expiresAtMillis") <= timestamp
        || !["WAITING", "MATCHED"].includes(room.get("status"))) {
        throw new HttpsError("permission-denied", "You are not a member of an active room.");
      }
      const otherUids = room.get("memberUids").filter((memberUid) => memberUid !== uid);
      const [messageSnapshot, rate, ...blocks] = await Promise.all([
        transaction.get(message),
        transaction.get(rateRef),
        ...otherUids.flatMap((otherUid) => [
          transaction.get(blockRef(uid, otherUid)),
          transaction.get(blockRef(otherUid, uid)),
        ]),
      ]);
      if (messageSnapshot.exists) return { messageId: message.id, duplicate: true };
      if (blocks.some((snapshot) => snapshot.exists)) {
        throw new HttpsError("permission-denied", "Room chat is unavailable because of a block.");
      }
      assertMessageRate(rate, timestamp, text);
      transaction.create(message, {
        senderUid: uid,
        text,
        status: "SENT",
        createdAt: FieldValue.serverTimestamp(),
        requestId,
      });
      transaction.set(roomRef, { lastChatAt: FieldValue.serverTimestamp() }, { merge: true });
      updateMessageRate(transaction, rateRef, rate, timestamp, text);
      return { messageId: message.id, recipients: otherUids };
    });
    if (!result.duplicate) {
      await Promise.all(result.recipients.map(async (recipientUid) => {
        const notificationId = await recordInAppNotification(
          recipientUid,
          "ROOM_MESSAGE",
          uid,
          { roomId }
        );
        await notify(recipientUid, "messages", {
          type: "ROOM_MESSAGE",
          actorUid: uid,
          notificationId,
          roomId,
        });
      }));
    }
    return { messageId: result.messageId, duplicate: result.duplicate === true };
  }

  async function getMessages(request, room = false) {
    const keys = room
      ? ["roomId", "pageSize", "beforeCreatedAtMillis", "beforeMessageId"]
      : ["conversationId", "pageSize", "beforeCreatedAtMillis", "beforeMessageId"];
    validateData(request.data || {}, keys);
    const uid = requireUid(request);
    const pageSize = request.data.pageSize ?? 30;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 50) {
      throw new HttpsError("invalid-argument", "pageSize must be 1-50.");
    }
    const parent = room
      ? db.collection("privateRooms").doc(validateUid(request.data.roomId))
      : db.collection("conversations").doc(validateRequestId(request.data.conversationId));
    const parentSnapshot = await parent.get();
    const validParent = parentSnapshot.exists
      && parentSnapshot.get("memberUids")?.includes(uid)
      && (!room || parentSnapshot.get("expiresAtMillis") > now());
    if (!validParent) throw new HttpsError("permission-denied", "You cannot read these messages.");
    let query = parent.collection("messages")
      .orderBy("createdAt", "desc")
      .orderBy(FieldPath.documentId(), "desc")
      .limit(pageSize + 1);
    if (request.data.beforeCreatedAtMillis !== undefined
      || request.data.beforeMessageId !== undefined) {
      if (!Number.isSafeInteger(request.data.beforeCreatedAtMillis)
        || typeof request.data.beforeMessageId !== "string") {
        throw new HttpsError("invalid-argument", "Message page cursor is invalid.");
      }
      query = query.startAfter(
        new Date(request.data.beforeCreatedAtMillis),
        request.data.beforeMessageId
      );
    }
    const snapshot = await query.get();
    const hasMore = snapshot.docs.length > pageSize;
    const docs = snapshot.docs.slice(0, pageSize);
    const messages = docs.map((doc) => ({
      messageId: doc.id,
      senderUid: doc.get("senderUid"),
      text: doc.get("text"),
      status: doc.get("status"),
      createdAtMillis: doc.get("createdAt")?.toMillis?.() ?? null,
    }));
    return {
      messages,
      nextCursor: hasMore
        ? {
          beforeCreatedAtMillis: docs[docs.length - 1].get("createdAt").toMillis(),
          beforeMessageId: docs[docs.length - 1].id,
        }
        : null,
    };
  }

  async function markConversationRead(request) {
    validateData(request.data, ["conversationId"]);
    const uid = requireUid(request);
    const conversationId = validateRequestId(request.data.conversationId);
    const ref = db.collection("conversations").doc(conversationId);
    await db.runTransaction(async (transaction) => {
      const conversation = await transaction.get(ref);
      if (!conversation.exists || !conversation.get("memberUids").includes(uid)) {
        throw new HttpsError("permission-denied", "You are not a member of this conversation.");
      }
      transaction.update(
        ref,
        new FieldPath("unreadCounts", uid),
        0,
        new FieldPath("lastReadAt", uid),
        FieldValue.serverTimestamp()
      );
    });
    return { read: true };
  }

  async function reportPlayer(request) {
    validateData(request.data, ["reportedUid", "reason", "details", "matchId", "messageId", "requestId"]);
    const uid = requireUid(request);
    const reportedUid = validateUid(request.data.reportedUid);
    const requestId = validateRequestId(request.data.requestId);
    if (reportedUid === uid) throw new HttpsError("invalid-argument", "You cannot report yourself.");
    if (!REASONS.has(request.data.reason)) throw new HttpsError("invalid-argument", "Choose a report reason.");
    const details = typeof request.data.details === "string" ? request.data.details.trim() : "";
    if (details.length > 500) throw new HttpsError("invalid-argument", "Report details must be at most 500 characters.");
    const ref = db.collection("reports").doc(`${uid}_${requestId}`);
    const rateRef = db.collection("reportRateLimits").doc(uid);
    const timestamp = now();
    await db.runTransaction(async (transaction) => {
      const [oldReport, rate, reportedProfile] = await Promise.all([
        transaction.get(ref),
        transaction.get(rateRef),
        transaction.get(db.collection("users").doc(reportedUid)),
      ]);
      if (oldReport.exists) return;
      if (!reportedProfile.exists) throw new HttpsError("not-found", "This player is unavailable.");
      const rateData = rate.exists ? rate.data() : {};
      const sameWindow = timestamp - (rateData.windowStartedAtMillis || 0) < REPORT_WINDOW_MILLIS;
      if (sameWindow && rateData.count >= REPORT_WINDOW_LIMIT) {
        throw new HttpsError("resource-exhausted", "You have submitted too many reports. Try again later.");
      }
      let relatedMessage = null;
      if (request.data.messageId !== undefined) {
        if (typeof request.data.messageId !== "string" || request.data.messageId.length > 256) {
          throw new HttpsError("invalid-argument", "Message reference is invalid.");
        }
        const directMessage = await transaction.get(
          db.collection("conversations").doc(
            pairConversationId(uid, reportedUid)
          ).collection("messages").doc(request.data.messageId)
        );
        if (directMessage.exists && directMessage.get("senderUid") === reportedUid) {
          relatedMessage = {
            conversationId: pairConversationId(uid, reportedUid),
            messageId: request.data.messageId,
          };
        }
      }
      let relatedMatchId = null;
      if (request.data.matchId !== undefined) {
        if (typeof request.data.matchId !== "string"
          || !/^[A-Za-z0-9_-]{8,80}$/.test(request.data.matchId)) {
          throw new HttpsError("invalid-argument", "Match reference is invalid.");
        }
        const match = await transaction.get(
          db.collection("onlineMatches").doc(request.data.matchId)
        );
        if (!match.exists || !match.get("playerUids").includes(uid)
          || !match.get("playerUids").includes(reportedUid)) {
          throw new HttpsError("permission-denied", "This match cannot be attached to the report.");
        }
        relatedMatchId = request.data.matchId;
      }
      transaction.create(ref, {
        reporterUid: uid,
        reportedUid,
        reason: request.data.reason,
        details,
        matchId: relatedMatchId,
        relatedMessage,
        status: "NEW",
        createdAt: FieldValue.serverTimestamp(),
      });
      transaction.set(rateRef, {
        windowStartedAtMillis: sameWindow ? rateData.windowStartedAtMillis : timestamp,
        count: sameWindow ? rateData.count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    return { submitted: true };
  }

  async function listReports(request) {
    validateData(request.data || {}, ["pageSize", "cursorId"]);
    const uid = requireUid(request);
    const pageSize = request.data.pageSize ?? 20;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > FRIEND_PAGE_SIZE_MAX) {
      throw new HttpsError("invalid-argument", `pageSize must be 1-${FRIEND_PAGE_SIZE_MAX}.`);
    }
    let query = db.collection("reports").where("reporterUid", "==", uid)
      .orderBy(FieldPath.documentId()).limit(pageSize + 1);
    if (request.data.cursorId) query = query.startAfter(validateRequestId(request.data.cursorId));
    const snapshot = await query.get();
    const docs = snapshot.docs.slice(0, pageSize);
    return {
      reports: docs.map((doc) => ({
        reportId: doc.id,
        reason: doc.get("reason"),
        status: doc.get("status"),
        createdAtMillis: doc.get("createdAt")?.toMillis?.() ?? null,
      })),
      nextCursorId: snapshot.docs.length > pageSize ? docs[docs.length - 1].id : null,
    };
  }

  async function reviewReport(request) {
    validateData(request.data, ["reportId", "status", "reviewNote"]);
    const uid = requireUid(request);
    if (request.auth.token.moderator !== true && request.auth.token.admin !== true) {
      throw new HttpsError("permission-denied", "Moderator authorization is required.");
    }
    const reportId = validateRequestId(request.data.reportId);
    const status = request.data.status;
    if (!["UNDER_REVIEW", "RESOLVED", "DISMISSED"].includes(status)) {
      throw new HttpsError("invalid-argument", "Choose a supported report review status.");
    }
    const reviewNote = typeof request.data.reviewNote === "string"
      ? request.data.reviewNote.trim()
      : "";
    if (reviewNote.length > 500) {
      throw new HttpsError("invalid-argument", "Review note must be at most 500 characters.");
    }
    const report = db.collection("reports").doc(reportId);
    const audit = db.collection("moderationAudit").doc(randomUUID());
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(report);
      if (!snapshot.exists) throw new HttpsError("not-found", "This report is unavailable.");
      transaction.set(report, {
        status,
        reviewerUid: uid,
        reviewNote,
        reviewedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      transaction.create(audit, {
        action: "REPORT_REVIEWED",
        reportId,
        moderatorUid: uid,
        previousStatus: snapshot.get("status"),
        newStatus: status,
        createdAt: FieldValue.serverTimestamp(),
      });
    });
    return { reviewed: true };
  }

  async function registerNotificationToken(request) {
    validateData(request.data, ["token", "deviceId"]);
    const uid = requireUid(request);
    const token = request.data.token;
    const deviceId = request.data.deviceId;
    if (typeof token !== "string" || token.length < 20 || token.length > 4096
      || typeof deviceId !== "string" || !/^[A-Za-z0-9_-]{8,100}$/.test(deviceId)) {
      throw new HttpsError("invalid-argument", "Notification device registration is invalid.");
    }
    await db.collection("notificationTokens").doc(uid).collection("devices").doc(deviceId).set({
      token,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { registered: true };
  }

  async function removeNotificationToken(request) {
    validateData(request.data, ["deviceId"]);
    const uid = requireUid(request);
    const deviceId = request.data.deviceId;
    if (typeof deviceId !== "string" || !/^[A-Za-z0-9_-]{8,100}$/.test(deviceId)) {
      throw new HttpsError("invalid-argument", "Notification device ID is invalid.");
    }
    await db.collection("notificationTokens").doc(uid).collection("devices").doc(deviceId).delete();
    return { removed: true };
  }

  async function setNotificationPreferences(request) {
    validateData(request.data, [
      "friendRequests", "roomInvitations", "messages", "matchUpdates", "socialUpdates",
    ]);
    const uid = requireUid(request);
    const prefs = request.data;
    if (Object.values(prefs).some((value) => typeof value !== "boolean")) {
      throw new HttpsError("invalid-argument", "Notification preferences must be boolean.");
    }
    await db.collection("notificationPreferences").doc(uid).set({
      ...prefs,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { saved: true };
  }

  async function getNotificationPreferences(request) {
    validateData(request.data || {}, []);
    const uid = requireUid(request);
    const snapshot = await db.collection("notificationPreferences").doc(uid).get();
    const stored = snapshot.data() || {};
    return {
      friendRequests: stored.friendRequests !== false,
      roomInvitations: stored.roomInvitations !== false,
      messages: stored.messages !== false,
      matchUpdates: stored.matchUpdates !== false,
      socialUpdates: stored.socialUpdates !== false,
    };
  }

  async function listNotifications(request) {
    validateData(request.data || {}, ["pageSize", "cursorCreatedAtMillis", "cursorId"]);
    const uid = requireUid(request);
    const pageSize = request.data.pageSize ?? 20;
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > FRIEND_PAGE_SIZE_MAX) {
      throw new HttpsError("invalid-argument", `pageSize must be 1-${FRIEND_PAGE_SIZE_MAX}.`);
    }
    let query = db.collection("inAppNotifications").doc(uid).collection("items")
      .orderBy("createdAt", "desc")
      .orderBy(FieldPath.documentId(), "desc")
      .limit(pageSize + 1);
    if (request.data.cursorCreatedAtMillis !== undefined || request.data.cursorId !== undefined) {
      if (!Number.isSafeInteger(request.data.cursorCreatedAtMillis)
        || typeof request.data.cursorId !== "string") {
        throw new HttpsError("invalid-argument", "Notification page cursor is invalid.");
      }
      query = query.startAfter(
        new Date(request.data.cursorCreatedAtMillis),
        validateRequestId(request.data.cursorId)
      );
    }
    const snapshot = await query.get();
    const hasMore = snapshot.docs.length > pageSize;
    const docs = snapshot.docs.slice(0, pageSize);
    return {
      notifications: docs.map((doc) => ({
        notificationId: doc.id,
        type: doc.get("type"),
        actorUid: doc.get("actorUid"),
        data: doc.get("data") || {},
        isRead: Boolean(doc.get("readAt")),
        createdAtMillis: doc.get("createdAt")?.toMillis?.() ?? null,
      })),
      nextCursor: hasMore
        ? {
          cursorCreatedAtMillis: docs[docs.length - 1].get("createdAt").toMillis(),
          cursorId: docs[docs.length - 1].id,
        }
        : null,
    };
  }

  async function markNotificationRead(request) {
    validateData(request.data, ["notificationId"]);
    const uid = requireUid(request);
    const notificationId = validateRequestId(request.data.notificationId);
    const ref = db.collection("inAppNotifications").doc(uid).collection("items").doc(notificationId);
    const snapshot = await ref.get();
    if (!snapshot.exists) throw new HttpsError("not-found", "Notification is unavailable.");
    await ref.set({ readAt: FieldValue.serverTimestamp() }, { merge: true });
    return { read: true };
  }

  return {
    acceptFriendRequest,
    acceptRoomInvitation,
    blockPlayer,
    cancelFriendRequest,
    cancelRoomInvitation,
    getDirectMessages: (request) => getMessages(request, false),
    getNotificationPreferences,
    getRoomMessages: (request) => getMessages(request, true),
    inviteFriendToRoom,
    listBlockedPlayers,
    listConversations,
    listFriendRequests,
    listFriends,
    listNotifications,
    listRoomInvitations,
    listReports,
    markConversationRead,
    markNotificationRead,
    openConversation,
    rejectFriendRequest,
    rejectRoomInvitation,
    removeFriend,
    removeNotificationToken,
    reportPlayer,
    reviewReport,
    expireRoomInvitations,
    registerNotificationToken,
    searchPlayerByGamerId,
    sendFriendRequest,
    sendDirectMessage,
    sendRoomMessage,
    setNotificationPreferences,
    unblockPlayer,
    updatePresence,
  };
}

module.exports = {
  createSocialService,
  MESSAGE_LIMIT,
  PRESENCE_TTL_MILLIS,
};
