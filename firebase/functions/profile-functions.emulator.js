const test = require("node:test");
const assert = require("node:assert/strict");
if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080") {
  throw new Error("Profile callable tests require the local Firestore Emulator at 127.0.0.1:8080.");
}
const {
  checkUsernameAvailability,
  createPlayerProfile,
  searchPublicPlayers,
  updatePlayerProfile,
} = require("./index");
const { createFirestoreClient } = require("./firestore-client");

const db = createFirestoreClient();

function request(uid, data, token = {}) {
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

test("profile callables create, read, edit, reserve, search, and resolve Gamer IDs", {
  timeout: 30_000,
}, async () => {
  const uid = `profile-test-${Date.now()}`;
  const username = `arena_${Date.now().toString().slice(-8)}`;
  const updatedUsername = `${username}_edit`;
  const profileInput = {
    username,
    displayName: "Emulator Player",
    avatarId: "ember",
  };

  try {
    const available = await checkUsernameAvailability.run(request(uid, { username }));
    assert.deepEqual(available, { available: true });

    await assert.rejects(
      createPlayerProfile.run({ data: profileInput }),
      (error) => error.code === "unauthenticated"
    );
    await assert.rejects(
      createPlayerProfile.run(request(uid, profileInput, {
        firebase: { sign_in_provider: "password" },
        email_verified: false,
      })),
      (error) => error.code === "permission-denied"
    );

    const created = await createPlayerProfile.run(request(uid, profileInput));
    assert.equal(created.uid, uid);
    assert.match(created.gamerId, /^AR-[0-9]{6}$/);
    assert.equal(created.profileVisibility, "private");

    const [user, privateAccount, usernameReservation, gamerIdReservation, publicPlayer] =
      await Promise.all([
        db.collection("users").doc(uid).get(),
        db.collection("privateAccounts").doc(uid).get(),
        db.collection("usernames").doc(username).get(),
        db.collection("gamerIds").doc(created.gamerId).get(),
        db.collection("publicPlayers").doc(uid).get(),
      ]);
    assert.equal(user.get("gamerId"), created.gamerId);
    assert.equal(user.get("username"), username);
    assert.equal(privateAccount.exists, true);
    assert.equal(usernameReservation.get("uid"), uid);
    assert.equal(gamerIdReservation.get("uid"), uid);
    assert.equal(publicPlayer.get("profileVisibility"), "private");

    const ownReservation = await checkUsernameAvailability.run(request(uid, { username }));
    const otherReservation = await checkUsernameAvailability.run(
      request(`${uid}-other`, { username })
    );
    assert.deepEqual(ownReservation, { available: true });
    assert.deepEqual(otherReservation, { available: false });

    const duplicate = await createPlayerProfile.run(request(uid, {
      ...profileInput,
      username: `${username}_alt`,
    }));
    assert.equal(duplicate.gamerId, created.gamerId);
    assert.equal(duplicate.username, username);

    await assert.rejects(
      createPlayerProfile.run(request(`${uid}-conflict`, profileInput)),
      (error) => error.code === "already-exists"
    );

    const updated = await updatePlayerProfile.run(request(uid, {
      ...profileInput,
      username: updatedUsername,
      displayName: "Updated Emulator Player",
      profileVisibility: "public",
    }));
    assert.equal(updated.gamerId, created.gamerId);
    assert.equal(updated.username, updatedUsername);
    assert.equal(updated.profileVisibility, "public");

    const [updatedUser, oldUsername, newUsername, gamerIdAfterUpdate, publicAfterUpdate] =
      await Promise.all([
        db.collection("users").doc(uid).get(),
        db.collection("usernames").doc(username).get(),
        db.collection("usernames").doc(updatedUsername).get(),
        db.collection("gamerIds").doc(created.gamerId).get(),
        db.collection("publicPlayers").doc(uid).get(),
      ]);
    assert.equal(updatedUser.get("displayName"), "Updated Emulator Player");
    assert.equal(updatedUser.get("profileVisibility"), "public");
    assert.equal(oldUsername.exists, false);
    assert.equal(newUsername.get("uid"), uid);
    assert.equal(gamerIdAfterUpdate.get("uid"), uid);
    assert.equal(publicAfterUpdate.get("profileVisibility"), "public");

    const gamerIdLookup = await db.collection("gamerIds").doc(created.gamerId).get();
    assert.equal(gamerIdLookup.get("uid"), uid);

    const results = await searchPublicPlayers.run(request(uid, {
      usernamePrefix: username.slice(0, 7),
    }));
    assert.equal(results.some((profile) => profile.uid === uid), true);

    await createPlayerProfile.run(request(`${uid}-private`, {
      ...profileInput,
      username: `${username.slice(0, 12)}_private`,
      displayName: "Private Emulator Player",
    }));
    const publicResults = await searchPublicPlayers.run(request(uid, {
      usernamePrefix: username.slice(0, 7),
    }));
    assert.equal(publicResults.some((profile) => profile.uid === uid), true);
    assert.equal(publicResults.some((profile) => profile.uid === `${uid}-private`), false);
  } finally {
    await db.collection("users").doc(uid).delete();
    await db.collection("privateAccounts").doc(uid).delete();
    await db.collection("publicPlayers").doc(uid).delete();
    await db.collection("usernames").doc(username).delete();
    await db.collection("usernames").doc(updatedUsername).delete();
    await db.collection("gamerIds").where("uid", "in", [uid, `${uid}-private`]).get()
      .then((snapshot) => Promise.all(snapshot.docs.map((document) => document.ref.delete())));
    await db.collection("users").doc(`${uid}-private`).delete();
    await db.collection("privateAccounts").doc(`${uid}-private`).delete();
    await db.collection("publicPlayers").doc(`${uid}-private`).delete();
  }
});

test.after(async () => {
  await db.terminate();
});
