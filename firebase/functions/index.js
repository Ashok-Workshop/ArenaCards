const { randomInt } = require("node:crypto");
const { initializeApp } = require("firebase-admin/app");
const { FieldPath, FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const Filter = require("bad-words");
const {
  containsProfanity,
  formatGamerId,
  normalizeUsername,
  playerLevel,
  validateUsername,
} = require("./profile-domain");

initializeApp();
const db = getFirestore();
const profanityFilter = new Filter();

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

exports.checkUsernameAvailability = onCall(async (request) => {
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

exports.createPlayerProfile = onCall(async (request) => {
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

exports.updatePlayerProfile = onCall(async (request) => {
  const uid = requireUid(request);
  const input = validateProfileInput(request.data);
  const visibility = String(request.data?.profileVisibility ?? "");
  if (!VISIBILITIES.has(visibility)) {
    throw new HttpsError("invalid-argument", "Choose a valid profile visibility setting.");
  }

  const userRef = db.collection("users").doc(uid);
  const usernameRef = db.collection("usernames").doc(input.usernameNormalized);
  const publicRef = db.collection("publicPlayers").doc(uid);

  return db.runTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
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
    return profileResponse(uid, { ...updated, updatedAt: new Date() });
  });
});

exports.searchPublicPlayers = onCall(async (request) => {
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
