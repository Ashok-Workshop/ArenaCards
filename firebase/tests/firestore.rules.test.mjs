import { readFile } from "node:fs/promises";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { after, before, test } from "node:test";

let environment;

before(async () => {
  environment = await initializeTestEnvironment({
    projectId: "demo-arenacards",
    firestore: {
      rules: await readFile(new URL("../../firestore.rules", import.meta.url), "utf8"),
    },
  });
  await environment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    await setDoc(doc(firestore, "users", "player-a"), {
      gamerId: "AR-827491",
      username: "arena_one",
      accountStatus: "active",
      wins: 0,
    });
    await setDoc(doc(firestore, "publicPlayers", "player-a"), {
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
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await setDoc(doc(firestore, "publicPlayers", "player-b"), {
      gamerId: "AR-827492",
      username: "arena_two",
      usernameNormalized: "arena_two",
      displayName: "Arena Two",
      avatarId: "tide",
      profileVisibility: "private",
    });
  });
});

after(async () => {
  await environment?.cleanup();
});

test("authenticated owner can read their private profile", async () => {
  const firestore = environment.authenticatedContext("player-a").firestore();
  await assertSucceeds(getDoc(doc(firestore, "users", "player-a")));
});

test("another user cannot read private profile documents", async () => {
  const firestore = environment.authenticatedContext("player-b").firestore();
  await assertFails(getDoc(doc(firestore, "users", "player-a")));
});

test("public approved profiles are readable but private profiles are not", async () => {
  const firestore = environment.unauthenticatedContext().firestore();
  await assertSucceeds(getDoc(doc(firestore, "publicPlayers", "player-a")));
  await assertFails(getDoc(doc(firestore, "publicPlayers", "player-b")));
});

test("clients cannot create or modify protected profile and index documents", async () => {
  const firestore = environment.authenticatedContext("player-a").firestore();
  await assertFails(setDoc(doc(firestore, "users", "player-a"), { wins: 1000 }, { merge: true }));
  await assertFails(setDoc(doc(firestore, "publicPlayers", "player-a"), { gamerId: "AR-000001" }, { merge: true }));
  await assertFails(setDoc(doc(firestore, "usernames", "admin"), { uid: "player-a" }));
  await assertFails(setDoc(doc(firestore, "gamerIds", "AR-000001"), { uid: "player-a" }));
});

test("public projection refuses documents that contain private fields", async () => {
  await environment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "publicPlayers", "player-secret"), {
      gamerId: "AR-827493",
      username: "player_secret",
      usernameNormalized: "player_secret",
      displayName: "Player Secret",
      avatarId: "wild",
      profileVisibility: "public",
      email: "private@example.com",
    });
  });
  const firestore = environment.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(firestore, "publicPlayers", "player-secret")));
});
