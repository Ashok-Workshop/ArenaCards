# ArenaCards build status

## Phase 2A - UI and offline game

**Status:** UI foundation remains implemented; offline game rules have been substantially extended. Additional polish and device validation remain.

- Premium Material 3 design system, onboarding, dashboard, navigation, responsive practice table, and local game state remain in place.
- The practice engine now uses a complete 108-card deck for two-to-four players, matching rules, Skip, Reverse, Draw Two, Wild, Wild Draw Four challenges, penalties, win/draw detection, discard reshuffling, and score values.
- Easy/medium/hard CPU choices use only the CPU's hand and public table/player-card counts.
- Pause/resume, CPU difficulty selection, and SavedStateHandle match restoration are implemented.
- Local sound/haptic/reduced-motion/theme preferences, generated feedback tones, local practice history, original Compose graphics, and accessible card states are implemented and covered by targeted tests. Compose UI/accessibility QA and physical-device verification remain outstanding; see [REMAINING_WORK.md](./REMAINING_WORK.md).
- Online multiplayer and server-authoritative wallet/rewards are implemented in source and emulator-tested, but are not deployed or production-verified.

## Phase 2B - Firebase Authentication

**Status:** Authentication code implemented. Firebase Console provider status and real-device behavior remain owner verification tasks.

- Firebase app initialization and Google Services Gradle plugin are configured.
- The Firebase Android package in `app/google-services.json` matches application ID `com.arenacards.game`. The supplied configuration was not modified.
- The matching client includes OAuth client types 1 and 3. `:app:processDebugGoogleServices` successfully generated the `default_web_client_id` resource. Its value is intentionally not exposed.
- Email/password registration (username, password confirmation, validation, verification), login, password reset, session state, and logout use Firebase Authentication.
- Google authentication uses AndroidX Credential Manager and Firebase credential exchange. Authenticated users can link Google; provider collisions are surfaced without silent account creation or merging.
- Phone OTP uses Firebase Phone Authentication callbacks, E.164 validation, country calling-code selection (India +91 default), six-digit manual entry, resend cooldown, resend token, error states, and authenticated phone linking. Firebase may also complete verification automatically.
- Email/password accounts remain on verification flow until Firebase reports verified. Google and phone accounts are recognized from Firebase provider state. Phone linking is blocked for an unverified email session.
- During Phase 2B, no Firestore documents/rules, public profiles, coin systems, or multiplayer backend were added. Phase 2C profile schema and rules are documented below.
- Firebase Console could not be inspected from the development environment. The owner reported enabling Google, Email/Password, and Phone, but that status, SMS region policy, and quota settings must still be confirmed in Console.
- No Google, email, or phone flow has been tested with a real device/account as part of this work.

## Validation - Phase 2B historical checkpoint

The following authentication/game baseline predates the offline-engine expansion below.

Ran from PowerShell with the cached JDK 17:

```powershell
.\gradlew.bat :feature:auth:testDebugUnitTest :feature:game:testDebugUnitTest :app:assembleDebug
```

Results:

- `:feature:auth:testDebugUnitTest` - passed, 16 tests, 0 failures.
- `:feature:game:testDebugUnitTest` - passed, 3 tests, 0 failures.
- `:app:assembleDebug` - passed.
- Google Services processing completed successfully; `default_web_client_id` exists in generated resources.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (about 15.4 MB).

## Manual setup and device testing

Follow [AUTHENTICATION_SETUP.md](./AUTHENTICATION_SETUP.md) for SHA-1/SHA-256 registration, Firebase provider and SMS policy checks, test phone numbers, quotas/cost considerations, and step-by-step real-device verification instructions. Do not treat successful unit tests or APK assembly as proof that Console settings, Google OAuth, email delivery, or SMS delivery work.

## Phase 2C - Player profiles and Firestore (baseline validation before challenge extension)

**Status:** Android profile client, trusted Cloud Functions source, and restrictive Firestore rules implemented. Backend/rules are not deployed.

- Authenticated UIDs are the internal profile keys. Existing profile documents route the user to Home; missing profiles route to profile setup.
- `feature:profile` includes setup/edit/view UI, username checks, avatar and display name selection, privacy choice, Gamer ID copy, join date, player level, and match stats.
- `data` reads the owner profile with a Firestore snapshot listener (Android Firestore offline cache is enabled by its SDK defaults) and calls the trusted profile functions for mutations.
- Gamer ID and normalized username reservations are created transactionally in `firebase/functions`; profile update cannot change Gamer ID, account status, or game statistics.
- `firestore.rules` denies client writes and protects owner/private and visibility-gated public profile reads.
- `firebase/tests/firestore.rules.test.mjs` - passed in local Firestore Emulator, 5 tests.
- `firebase/functions/profile-domain.test.js` - passed, 5 tests.
- `:feature:profile:testDebugUnitTest` - passed, 11 tests, 0 failures.
- `:feature:auth:testDebugUnitTest` - passed, 16 tests, 0 failures.
- `:feature:game:testDebugUnitTest` - passed, 28 tests, 0 failures at this historical checkpoint.
- `:app:assembleDebug` - passed.
- `firebase/functions` domain helper tests - passed, 5 tests.
- Firestore Emulator rules tests - passed, 5 tests.
- Final APK: `app/build/outputs/apk/debug/app-debug.apk` (about 19.6 MB).
- Android Gradle validation used JDK 17; Firestore Emulator validation used the Android Studio JDK 25 because the current Firebase CLI requires JDK 21 or newer.
- `npm audit --omit=dev` in `firebase/functions` reported 0 known vulnerabilities. The local emulator/test toolchain reports 13 moderate findings in development-only dependencies; see [FIRESTORE_SECURITY.md](./FIRESTORE_SECURITY.md).
- No Firebase services were deployed and no paid plan/services were enabled. First-time profile creation/edit will remain unavailable until the Cloud Functions/rules are deployed after owner approval.

## Latest offline game validation

After adding Wild Draw Four challenges, the complete Android command passed:

```powershell
.\gradlew.bat :feature:game:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:profile:testDebugUnitTest :app:assembleDebug
```

- Game engine and ViewModel: 37 tests passed, 0 failures.
- Authentication: 16 tests passed, 0 failures.
- Player profiles: 11 tests passed, 0 failures.
- `:app:assembleDebug`: passed; debug APK is about 18.7 MB.
- Game tests passed again after the test-only random-injection correction, including seeded-session consistency; APK assembly also succeeded.
- No real-device testing or production Firebase integration was performed.

See [PLAYER_PROFILE_SETUP.md](./PLAYER_PROFILE_SETUP.md), [FIRESTORE_DATA_MODEL.md](./FIRESTORE_DATA_MODEL.md), and [FIRESTORE_SECURITY.md](./FIRESTORE_SECURITY.md) for schema, Console prerequisites, deployment boundary, and testing instructions.

## Latest offline experience polish

- Latest `:feature:game:testDebugUnitTest`: 40 passed; `:app:testDebugUnitTest`: 2 passed; `:app:assembleDebug`: passed.
- Settings/history, DataStore persistence, generated tones/haptics, reduced-motion controls, original Compose-drawn graphics, and offline practice statistics are implemented.
- These results do not cover Compose UI behavior, screen-reader experience, device performance, or final owner mobile QA.

## Batch 4 social/backend implementation

- Added source for Gamer ID friends/requests, block/unblock, presence, private-room invitations, direct/room chat, message/report moderation, notification preferences/FCM/in-app records, and scheduled timeout/invitation-expiry processing.
- Android now includes Friends & Invitations, live paginated chat with reporting, notification center/preferences/permission/deep-link flow, presence heartbeat, and private-room invitation/rule controls.
- Private rooms support 30/45/60-second turns, optional Wild Draw Four challenges, standard mode, and no stacking; changes lock when the room starts.
- Validation: Cloud Functions unit tests (16), Functions Emulator scenarios (16), Firestore rules Emulator scenario (1), Android app assembly, and unit-test tasks for `app`, `data`, `feature:multiplayer`, `feature:friends`, `feature:chat`, and `feature:notifications` passed.
- All backend source/rules remain undeployed. Live FCM, production/Enterprise behavior, scheduled-job activation, and physical-device UX remain unverified. Keep deployment and billing changes pending explicit owner approval.

## Batch 5 competitive implementation

- Added verified-result Elo ratings, global/friend/season leaderboards, privacy/block filtering, owner-admin season lifecycle, hourly scheduled season finalization, single-elimination tournaments, assigned multiplayer matches, competitive achievements, and server-owned rewards.
- Android now includes the leaderboard/profile/season/tournament entry screen and callable repository.
- Validation: 22 Functions unit tests, 17 Functions Emulator scenarios, 1 Firestore rules Emulator scenario, 87 Android unit tests, and `:app:assembleDebug` passed.
- No production season/tournament was created, and no Functions/rules/indexes were deployed. Enterprise behavior, production IAM, live delivery, and physical-device QA remain unverified.
