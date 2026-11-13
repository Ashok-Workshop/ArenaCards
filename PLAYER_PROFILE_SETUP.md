# ArenaCards player profile setup

## What is implemented

- Firebase Authentication UID is the immutable internal player identity. A successful authenticated session checks for `users/{uid}`; existing profiles continue to Home, and accounts without a profile are routed to profile setup.
- The setup screen collects a username, display name, and avatar. A profile starts with private visibility. Existing players can edit username, display name, avatar, and visibility, and can copy their permanent Gamer ID.
- The Android repository reads only the signed-in user's `users/{uid}` document. Firestore's Android SDK local cache and snapshot listener support cached profile display and subsequent server synchronization.
- The Android app does not allocate Gamer IDs, reserve usernames, write profile documents, or edit statistics. Those mutations call the trusted callable backend in `firebase/functions`.
- The backend uses Firebase Authentication UID, validates normalized names and reserved/profane terms, and allocates Gamer IDs and username reservations transactionally.
- No email, phone number, password, authentication token, wallet, or private authentication metadata is stored in the public player projection. Authentication contacts remain in Firebase Authentication.
- Firestore rules deny all client writes. Private profile data is owner-readable; the public projection is readable only when its visibility is `public`.

## Important deployment boundary

The Cloud Functions source and Firestore rules are prepared but **not deployed**. The app's first-time profile creation/edit and username availability checks require the callable functions to be deployed; until then the app presents an explicit service error instead of creating an insecure client-side profile.

Do not deploy or enable paid services without owner approval. Cloud Functions deployments commonly require the Firebase Blaze billing plan and can incur Cloud Build, Artifact Registry, function invocation, or Firestore costs. Confirm current Firebase pricing, project region, quotas, App Check plan, and billing requirements in the Firebase Console before enabling or deploying anything.

## Required Firebase Console actions

1. Select the same Firebase project registered in `app/google-services.json`.
2. Confirm Firebase Authentication is enabled and all intended providers are configured. Email/password profiles can only be created after email verification. Google and phone users must have a successful Firebase-authenticated session.
3. Create or confirm a Cloud Firestore database and select its location carefully. Function and database locations should be selected together; the source currently uses Cloud Functions' default `us-central1` region. Changing regions later may require data migration.
4. Review the project billing plan and service quotas. Do not upgrade billing or enable paid services without approval.
5. Before function deployment, review the username moderation policy and `bad-words` dependency for ArenaCards' target languages, false positives, and abuse reporting requirements. The profanity package is a basic filter, not comprehensive content moderation.
6. Configure Firebase App Check for Android and test it before enabling callable enforcement. App Check enforcement is not enabled in these source files, so it must be considered before production rollout.
7. Review the rules and test them in the Firestore Emulator. Keep rules deny-by-default; do not make Firestore public.
8. Only after explicit approval, install the Functions dependencies in `firebase/functions`, select the intended Firebase project, and deploy reviewed Functions/rules using the Firebase CLI. Do not deploy from this task.

No service-account key is needed or included. Do not download or commit service-account credentials.

## Local emulator validation

Requirements: Node.js supported by the installed Firebase CLI and a JDK 21 or newer. Cloud Functions are configured for the Node.js 22 runtime. From PowerShell in `firebase`:

```powershell
npm.cmd install
npm.cmd run test:rules
```

The backend's dependency-free policy helpers can be tested from `firebase/functions`:

```powershell
npm.cmd install
npm.cmd test
```

The test command launches only the local Firestore Emulator with a demo project; it does not connect to or deploy to a Firebase project.

## Test a new account

After an approved deployment of the callable functions and reviewed Firestore rules:

1. Install the debug APK on a device with internet access and sign in using a verified email account, Google, or Firebase Phone Authentication.
2. On first authenticated sign-in, the app checks `users/{Firebase UID}` and displays profile setup when no profile exists.
3. Select an available username, display name, and avatar. Use **Check availability** for feedback, then **Create player profile**. The trusted function atomically claims the normalized username and a collision-checked `AR-######` Gamer ID.
4. Confirm profile creation reaches Home, and open **Profile** to verify the identity, join date, initial level, and zeroed statistics.
5. Attempt to claim the same username from a second test account at nearly the same time. Only one transaction should succeed; the other account should receive a username conflict and choose another.
6. Confirm Firestore shows no password, email, phone, or token in `users/{uid}` or `publicPlayers/{uid}`.

## Test an existing account

1. Sign in again with an account whose Firebase UID already has `users/{uid}`.
2. Confirm it proceeds to Home without profile setup.
3. Open **Profile**, edit display name/avatar/visibility, and optionally change username to an unused value. Check that the Gamer ID, account status, and match statistics remain unchanged.
4. Sign in on a second device and confirm the same UID/profile loads. After disconnecting the network, cached profile display may be available; edits require the callable backend and network.
5. Sign out and confirm normal authentication behavior remains intact.

Do not use production accounts or alter rules to test untrusted writes. The automated rule tests use seeded emulator documents and do not test deployed functions or production security.
