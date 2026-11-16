# ArenaCards master project context

Updated: 2026-11-14

## Project

ArenaCards is an existing modular Android card game. Continue in this repository; do not recreate the project, remove working features, replace the game engine, deploy Firebase, enable billing, or change production data without explicit approval.

- Android application ID: `com.arenacards.game`
- Firebase project reported by the owner: `arenacards-f3279`
- Firestore database reported by the owner: `unoashok` (Firestore Native, `asia-south1`)
- Functions source region: `us-central1`
- Firebase Android BoM: `33.7.0` (resolved Firestore SDK `25.1.1`, callable Functions SDK `21.1.0`)
- Cloud Functions runtime: Node.js `22`; `firebase-functions` `7.4.0`; direct `@google-cloud/firestore` `9.4.1`
- GitHub: private repository `https://github.com/Ashok-Workshop/ArenaCards`, branch `main`
- Kotlin, Jetpack Compose, Material 3, Firebase Authentication, Firestore, and callable Functions source

## Implemented foundations

- Modular Android project, theme/design system, onboarding, dashboard, navigation, auth UI and Firebase Authentication source.
- Google, email/password, email verification, password recovery, phone OTP, provider linking, persisted Firebase sessions, and logout.
- Firebase UID-keyed player profile UI and repository, profile visibility, usernames, Gamer IDs, Firestore rules, and profile callable Functions source.
- Offline CPU practice now has a standard 108-card deck, two-to-four seats, color/value matching, Skip, Reverse, Draw Two, Wild, Wild Draw Four challenge/penalty resolution, scoring, win/draw handling, discard reshuffling, three CPU difficulty options, pause/resume, and `SavedStateHandle` restoration. Draw stacking is disabled. See [GAME_RULES.md](./GAME_RULES.md).
- The current practice setup hides the CPU difficulty chooser; existing engine strategies are retained. Card taps play directly, and drag-to-discard is an additional interaction rather than the only way to play.
- Offline games do not award competitive coins or online results.
- Batch 2 now adds a server-authoritative wallet, UTC daily rewards/streaks, daily-login and three-day-streak missions, separate XP/level progression, three-/seven-day achievements with immutable unlock history, and paginated coin-ledger history. The Android wallet screen consumes callable-Function responses; clients cannot directly read or write wallet/progression documents under current rules.
- Batches 3–5 provide server-authoritative online matchmaking, configurable private-room rules, scheduled turn/invitation expiry source, friend requests/lists, blocks, presence, private-room invitations, direct and room chat, reports/moderator review, notifications, verified match history and rewards, Elo-based competitive profiles/leaderboards, seasons, single-elimination tournaments, and competitive achievements. AdMob SSV, coin spending, full administrative tooling, and production rollout remain unfinished.
- Device-local experience settings cover generated sound effects/volume, haptics, reduced motion, and theme; local practice history tracks totals, recent matches, and milestones without writing to Firebase.
- Original card and brand graphics are drawn with Compose; optional feedback uses Android `ToneGenerator`. Background music and bundled sound assets are not present.
- Batch 6 visual refinement adds a tokenized shape/spacing foundation, elevated outlined panels, original profile avatar emblems, ambient felt table treatment, event-keyed card reveals, selection tilt, and narrow-screen layout adaptations without changing game state/rules, profile schemas, or stored avatar IDs.
- The in-progress Batch 6.1 pass adds a personalized home dashboard, a live offline/online selection flow, clearer practice and server-authoritative multiplayer tables, action confirmations, direct tap/drag interactions, dark-by-default new installs, theme-aware system bars, an original launcher/splash treatment, and a ten-second UNO announcement/catch rule with a two-card penalty. It does not yet satisfy the full screen-by-screen visual brief; complete accessibility/performance QA remains outstanding.

## Verified boundaries

- Firebase Cloud Functions and Firestore rules are source only and have not been deployed.
- Android profile reads now select `unoashok` explicitly; callable Functions use the official Google Cloud Firestore client with explicit project/database IDs and runtime Application Default Credentials.
- Firebase CLI rules/emulator configuration targets `unoashok`. Emulator tests do not establish production connectivity or deployment state.
- The Functions source no longer initializes Firestore through Firebase Admin; its unused Admin SDK dependency was removed rather than using the Admin named-database preview API.
- No billing or production Firebase services were enabled, and no production data was changed.
- `app/google-services.json` and local `local.properties` are intentionally ignored by Git. A new clone needs the owner’s local Firebase config and Android SDK setup.
- No real-device auth, gameplay, or production Firebase integration test has been performed.
- Online multiplayer and Batch 4 social features remain source-only: callable Functions, rules, and Android integration are not deployed or production-verified. Matchmaking uses human players only. Private rooms support 30/45/60-second turns, standard mode, Wild Draw Four challenge on/off, and no stacking; changes lock when a room starts. A scheduled timeout worker now exists in source, but will not run until approved deployment and may require Cloud Scheduler/billing setup.
- Batch 5 competitive ratings, leaderboards, season administration/finalization, tournament registration/brackets, match assignment, achievement unlocks, and rewards are server-owned and source-only. The Android leaderboard/tournament entry point is implemented; no season or tournament has been activated in production.
- Current offline polish validation: 40 game unit tests, 2 app-local persistence tests, debug APK assembly, and four Compose instrumentation tests on API 35 passed. The older API 37 Espresso run failed before assertions (`InputManager.getInstance` missing). TalkBack, font scaling, online navigation, and device performance checks remain manual QA items.
- Batch 2 validation: Functions unit tests (11), Functions Firestore Emulator scenarios (4), rules Emulator scenario (1), wallet ViewModel tests (3), app tests (2), and debug APK assembly passed. Local Node is 24.22.1, not the configured Node 22 runtime; production execution remains unverified.
- Batch 3 validation: Functions unit tests (16), Functions Emulator scenarios (11), rules Emulator scenario (1), multiplayer ViewModel tests (4), wallet/profile data configuration tests (2), offline game tests (40), app persistence tests (2), and debug APK assembly passed. Emulator coverage does not verify production or Enterprise-only behavior.
- Batch 4 validation: Functions Emulator scenarios (16) and Firestore rules Emulator scenario (1) passed. Android debug assembly and unit tests passed for app, data, multiplayer, friends, chat, and notifications; social ViewModel tests cover Gamer ID requests, error display, direct messaging/read state, message pagination, and notification preferences. The emulator uses standard edition; live FCM delivery, production access, and real-device behavior are not verified.
- Batch 5 validation: Functions unit tests (22), Functions Emulator scenarios (17), Firestore rules Emulator scenario (1), 87 Android unit tests, and `:app:assembleDebug` passed. The emulator uses standard edition; no production, Enterprise-specific, or physical-device verification was performed.
- App Check enforcement remains opt-in via `ARENA_ENFORCE_APP_CHECK=true` and is currently off; Android provider and Firebase Console configuration are not complete.
- Review of source/test code does not establish production readiness or guarantee backend security after deployment.
- This work remains local and uncommitted; do not commit or push until the owner explicitly confirms.

## Modules and key locations

- `app`: Android entry point and navigation integration.
- `core/designsystem`, `core/navigation`: shared Compose theme/components and destinations.
- `feature/auth`, `feature/profile`, `feature/home`, `feature/game`, `feature/multiplayer`, `feature/friends`, `feature/chat`, `feature/notifications`, and `feature/leaderboard`: feature UI/state and unit tests.
- `data`: Firebase authentication, profile, wallet, multiplayer, social, and competitive repositories.
- `firebase/functions`: trusted profile, wallet, multiplayer, social, moderation, FCM, competitive rating/season/tournament, and scheduled-processing source.
- `firestore.rules`, `firebase/tests`: restrictive rules source and Firestore Emulator tests.
- `BUILD_STATUS.md`, `REMAINING_WORK.md`, and this directory’s master/handover documents: current state and follow-up actions.

See [README.md](./README.md), [AUTHENTICATION_SETUP.md](./AUTHENTICATION_SETUP.md), [PLAYER_PROFILE_SETUP.md](./PLAYER_PROFILE_SETUP.md), [FIRESTORE_DATA_MODEL.md](./FIRESTORE_DATA_MODEL.md), and [FIRESTORE_SECURITY.md](./FIRESTORE_SECURITY.md) for detailed setup and design.

## Development and final QA sequencing

Next priorities are physical-device/accessibility QA after the planned feature phases are complete, then owner-approved Firebase deployment readiness, account-deletion/retention planning, and release readiness. AdMob SSV, coin spending, and full administrative tooling remain unfinished. The owner plans to install and test on a mobile device during final QA; keep offline results noncompetitive. Do not commit/push, deploy Firebase, enable billing, or publish without explicit owner approval.
