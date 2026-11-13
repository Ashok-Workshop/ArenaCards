# ArenaCards build status

## Phase 2A - UI and offline game

**Status:** Implemented; existing game functionality preserved.

- Premium Material 3 design system, onboarding, dashboard, navigation, responsive practice table, and local game state remain in place.
- The existing game engine was not replaced or modified for this authentication work.
- Online multiplayer, coin balances/transactions, and rewards are not implemented.

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

## Validation

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

## Phase 2C - Player profiles and Firestore

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
- `:feature:game:testDebugUnitTest` - passed, 3 tests, 0 failures.
- `:app:assembleDebug` - passed.
- `firebase/functions` domain helper tests - passed, 5 tests.
- Firestore Emulator rules tests - passed, 5 tests.
- Final APK: `app/build/outputs/apk/debug/app-debug.apk` (about 19.6 MB).
- Android Gradle validation used JDK 17; Firestore Emulator validation used the Android Studio JDK 25 because the current Firebase CLI requires JDK 21 or newer.
- `npm audit --omit=dev` in `firebase/functions` reported 0 known vulnerabilities. The local emulator/test toolchain reports 13 moderate findings in development-only dependencies; see [FIRESTORE_SECURITY.md](./FIRESTORE_SECURITY.md).
- No Firebase services were deployed and no paid plan/services were enabled. First-time profile creation/edit will remain unavailable until the Cloud Functions/rules are deployed after owner approval.

See [PLAYER_PROFILE_SETUP.md](./PLAYER_PROFILE_SETUP.md), [FIRESTORE_DATA_MODEL.md](./FIRESTORE_DATA_MODEL.md), and [FIRESTORE_SECURITY.md](./FIRESTORE_SECURITY.md) for schema, Console prerequisites, deployment boundary, and testing instructions.
