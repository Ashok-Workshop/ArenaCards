# ArenaCards master build status

Updated: 2026-11-14

## Batch 6.1 product-experience pass (in progress)

- Reworked Home into a personalized dashboard using the existing profile, wallet, and progression state, with explicit offline/online entry points and routes to notifications and existing features. The game-selection screen now routes offline practice to the local game and online play to the actual multiplayer lobby.
- Refined the practice and multiplayer tables with clearer status, draw/discard affordances, direct tap-to-play and drag-to-discard, and confirmations for pause/restart/exit and online navigation. Tapping remains the accessible alternative to dragging. Online actions still wait for server-observed state; no optimistic table updates were added.
- Removed the CPU difficulty chooser and separate “Play selected” action from the gameplay UI. Existing engine strategies and game rules are otherwise preserved in this pass.
- New installations default to the dark theme while existing explicit theme choices remain respected. Added theme-aware system-bar icon contrast, an original launcher icon, and a dark splash theme.
- Local and online engines now support UNO announcement and missed-call handling: announce within ten seconds, allow another participant to catch the call, and draw up to two cards for a missed call. Local practice saves the deadline with backward-compatible decoding; online calls/catches are server-validated and pending UNO identity/deadline are published without exposing hands.
- Latest Android verification: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:feature:game:testDebugUnitTest`, `:feature:multiplayer:testDebugUnitTest`, and `:app:connectedDebugAndroidTest` passed. All four Compose instrumentation tests, including drag-to-discard, completed on API 35 with zero failures.
- UNO follow-up validation: 42 game, 5 multiplayer, and 2 data Android unit tests passed; debug assembly and Android-test compilation passed; all four API 35 Compose tests passed. Cloud Functions unit tests passed (23), and the isolated multiplayer Firestore Emulator suite passed (9), including UNO authorization, catch penalty, and public-state checks. The full Functions Emulator suite had one combined-run competitive leaderboard failure (expected 3, actual 4); that competitive test passed alone (1/1). See [TESTING_STATUS.md](./TESTING_STATUS.md).
- The previous API 37 emulator failed Espresso initialization before assertions. The API 35 emulator was used successfully to inspect onboarding, home, offline setup, practice table, pause, and exit-confirmation screens.
- This is not completion of the full product-wide prompt. Full screen-by-screen visual/accessibility QA, audio design beyond generated feedback tones, and performance/device profiling remain outstanding. No Firebase deployment, production-data change, billing change, commit, push, or publication occurred.

## Batch 6 premium visual refinement

- Shared design components now have refined spacing/shape tokens, subtle panel elevation and borders, readable card foregrounds, selected-card lift/tilt, original illustrated avatar emblems driven by existing avatar IDs, and a procedural felt table backdrop.
- Offline and online tables use a shared ambient background and a stable-card-ID-triggered flip reveal. Reduced motion skips the flip and selection transforms; game rules and multiplayer state remain unchanged.
- Home profile actions and practice controls are adapted for narrow layouts. Profile avatar selection now previews the original artwork instead of emoji while preserving stored IDs.
- Added Compose instrumentation tests for card accessibility/tap behavior, disabled card semantics, and avatar identity.
- Final validation: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:feature:game:testDebugUnitTest`, changed feature-module Kotlin compilation, and `:app:compileDebugAndroidTestKotlin` passed. The two Android unit-test tasks reported 2 app-local and 40 game tests.
- Installed and launched the debug app on the API 37 emulator and visually inspected the narrow home screen/avatar. The connected Compose tests did not reach test assertions: all three stopped in Espresso initialization because this API 37 image lacks `InputManager.getInstance`. Re-run on a supported stable API image; this is a test-environment incompatibility, not a passing UI test.
- No downloaded/copyrighted art, font, music, or audio asset was added. No production service, commit, or push was touched.

## Offline game checkpoint validation (preceding work)

Command:

```powershell
.\gradlew.bat :feature:game:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:profile:testDebugUnitTest :app:assembleDebug
```

Latest run after Wild Draw Four challenge implementation:

- Game engine and ViewModel unit tests: **37 passed**, 0 failures.
- Authentication unit tests: **16 passed**, 0 failures.
- Player profile unit tests: **11 passed**, 0 failures.
- `:app:assembleDebug`: **BUILD SUCCESSFUL**.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (about 18.7 MB).

After a test-constructor correction, `:feature:game:testDebugUnitTest` passed again (37 tests, 0 failures) and `:app:assembleDebug` succeeded. The full command also passed immediately before that correction; auth/profile source was unchanged.

That earlier Gradle run used cached JDK 17 and validated the offline-game change before the Firestore database correction below.

## Named Firestore database correction

- Android profile repository now uses the supported named-database API for `arenacards-f3279` / `unoashok`, validates the Firebase project ID, and configures callable Functions explicitly for the existing `us-central1` endpoint.
- Cloud Functions use `@google-cloud/firestore` `9.4.1` with explicit project/database IDs and runtime Application Default Credentials. Firebase Admin named-database preview APIs are not used; the unused direct Admin dependency was removed.
- `firebase.json` associates Firestore rules and emulator tests with `unoashok`; no index file or composite index was added because current profile queries use automatic single-field indexing.
- Functions config/domain tests: **8 passed**.
- Callable profile flows against the Firestore Emulator: **1 integration scenario passed**, covering profile creation/idempotence, reads, edits, username reservations, Gamer ID index lookup, public search/visibility, and authentication checks.
- Named-database security rules against Firestore/Auth Emulators: **1 scenario passed**, covering owner/cross-user/private/public reads and protected writes.
- Android `:data:testDebugUnitTest` passed, including named-project/database configuration tests. The full game/auth/profile/data test command completed successfully; unchanged tasks were up-to-date. `:app:assembleDebug` succeeded.
- The local Firestore Emulator is standard edition; Enterprise-specific production behavior, live connectivity, IAM, and deployment remain unverified. Local Node was `24.22.1` while the Functions runtime target remains Node `22`.
- Android command: `.\gradlew.bat :feature:game:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:profile:testDebugUnitTest :data:testDebugUnitTest :app:assembleDebug`. Existing game/auth/profile results were up-to-date (37/16/11 tests); the new data configuration suite reports 2 passed; debug assembly succeeded.
- The latest Android command used Android Studio JBR `25.0.3` with Gradle `9.3.1`.

## Offline experience polish (2026-11-14)

- Added persistent, device-local controls for generated sound effects, effect volume, haptics, reduced motion, and light/dark/system theme. Preferences use AndroidX DataStore; individual changes are applied to the latest stored value to avoid rapid-toggle lost updates.
- Added local practice totals, a 30-match recent history, and local-only milestones. Completed match IDs are deduplicated against recent history; offline practice does not award competitive currency or update online profiles.
- Added original Compose-drawn card faces, card back, Wild emblem, and ArenaCards logo mark; no commercial card-game artwork, image files, bundled music, or third-party audio assets were introduced. Game card IDs now serve as stable lazy-list keys; legacy serialized sessions remain decodable.
- Added card accessibility descriptions, playable/disabled/selected semantics, game status live-region feedback, and a reduced-motion option for selection and splash animation.
- Feedback sounds are generated with Android `ToneGenerator`; no background music or production audio assets are included.
- Latest validation: `:feature:game:testDebugUnitTest` (40 tests), `:app:testDebugUnitTest` (2 tests), and `:app:assembleDebug` all passed. Game tests include completed-match recording; app tests cover DataStore preference writing and duplicate local match recording.
- No Compose UI test, screen-reader audit, physical-device test, Firebase deployment, production connectivity, billing change, or production-data change was performed.

## Backend and production status

- Cloud Functions source and Firestore rules are not deployed.
- The client and backend source target `arenacards-f3279` / `unoashok`; production database connectivity has not been tested.
- Production authentication/profile behavior and real-device gameplay remain unverified.
- No billing plan or paid service was enabled; no production Firebase data was changed.
- Do not infer production readiness from a successful local build or emulator/unit tests.

## Server-authoritative wallet and progression (2026-11-14)

- Added callable Functions for authenticated account snapshots, UTC daily-reward claims, mission claims, and paginated transaction history. Backend writes are atomic across wallet balances, immutable coin ledger entries, daily claims, missions, XP events, achievement unlocks/history, and profile level projections.
- Wallet invariant is `totalCoinsEarned - totalCoinsSpent == coinsBalance`; current features only award coins and do not implement spending. Daily rewards are 50/75/100/150/200/250/500 coins by UTC streak day, capped at day seven. Streak and claim idempotency are enforced server-side.
- Daily-login and three-day-login-streak missions are active. Online-match mission progress is not yet integrated with verified match events; match-end rewards are separately implemented in Batch 3. XP is distinct from coin balance; achievements track three- and seven-day streaks.
- Android wallet screen displays callable-backed balance, reward status, missions, progression, achievements/unlock history, and transaction history with loading/error/retry behavior.
- Validation after the latest integrity/history changes: Cloud Functions unit tests **11 passed**; Firestore Functions emulator tests **4 passed**; Firestore rules emulator test **1 passed**; Android game/auth/profile/data regression tests **40/16/11/2 passed**; wallet ViewModel tests **3 passed**; app unit tests **2 passed**; `:app:assembleDebug` succeeded. Emulator logged expected ADC metadata lookup warnings; it used the standard emulator and did not connect to production.
- Local Node is `24.22.1` rather than the configured Functions runtime `22`; Node 22-specific execution is not locally verified. Functions and rules remain undeployed, App Check enforcement is off, and no production services, data, billing, or Ads configuration changed.
- AdMob/SSV was not implemented: it requires owner-managed AdMob configuration and trusted verification. No client assertion can award ad coins. Physical-device, accessibility, and Compose UI testing remain pending.

See [BUILD_STATUS.md](./BUILD_STATUS.md), [REMAINING_WORK.md](./REMAINING_WORK.md), and [PENDING_MANUAL_ACTIONS.md](./PENDING_MANUAL_ACTIONS.md).

## Server-authoritative multiplayer foundation (Batch 3, 2026-11-14)

- Added a server-side card engine and callable Functions for human-only matchmaking, private code rooms, ready/start/leave, server-validated card/draw/pass/challenge actions, reconnect/heartbeat, turn expiry, verified result history, and atomic wallet/XP/stat updates.
- Public match documents contain public gameplay state only. The draw pile, full hands, and Wild Draw Four legality remain in server-only state; each authenticated participant can read only their own private hand. Client writes to online match, room, status, and history documents remain denied.
- Verified completion awards 25 coins and 50 XP to each participant, plus 75 coins and 50 XP to the winner. The result/reward operation is idempotent and transactional. Abandoned and stalemate matches do not receive winner rewards.
- Android now has a signed-in multiplayer route, online lobby, private room, realtime table, action/timeout handling, match history, and sound/haptic feedback integration. Actions are not presented as authoritative until Firestore confirms the versioned server update.
- At the Batch 3 checkpoint (superseded by Batch 4 below), friends/chat/notifications and configurable room rules were not implemented; room settings were fixed and timeout processing was client-triggered. Batch 4 adds those source features and scheduled timeout processing.
- Functions/Rules remain undeployed. No production data, billing, Firebase settings, or deployed region changed. App Check enforcement remains disabled; no production performance/cost/scale test was performed.

### Batch 3 validation

- `:feature:multiplayer:testDebugUnitTest`: 4 passed.
- `:data:testDebugUnitTest`: 2 passed; `:feature:game:testDebugUnitTest`: 40 passed; `:app:testDebugUnitTest`: 2 passed.
- `:app:assembleDebug`: passed.
- `npm.cmd --prefix firebase/functions test`: 16 passed.
- `npm.cmd --prefix firebase run test:functions:emulator`: 11 passed, including multiplayer room/block checks, stale action rejection, backend deadline enforcement, verified rewards, profile, and wallet flows.
- `npm.cmd --prefix firebase run test:rules`: 1 passed, including participant-only match/hand, room, history, and client-write denials.
- Emulator uses standard edition and local Node `24.22.1` versus configured Functions Node `22`; Enterprise behavior and production execution remain unverified. Emulator emitted expected ADC metadata lookup warnings.

## Social, chat, notifications, and room rules (Batch 4)

- Added trusted Functions source for Gamer ID player lookup, symmetric friend requests, friend/block management, heartbeat presence, room invitations, direct/room messaging, user reports and moderator review, in-app notifications, FCM token/preferences, and invitation expiry.
- Added server-scheduled multiplayer timeout processing with transactional per-turn idempotency, no-show abandonment, and standard private-room settings (30/45/60-second turns, Wild Draw Four challenge on/off, stacking disabled). The timeout worker and invitation expiry remain source-only and are not active until deployment.
- Android now includes Friends & Invitations screens, live direct/room chat with paged history and reporting, in-app notification center/preferences, Android 13+ notification permission UX, FCM token registration, notification deep-link routing, foreground presence heartbeats, and private-room invite/rule controls.
- Firestore rules keep social records server-write-only and restrict reads to owners, members, or participants. Message bodies are excluded from FCM payloads; client writes remain denied.
- Cloud Functions unit tests: **16 passed**. Functions Firestore Emulator suite: **16 passed**, including social, chat, invitation, report, timeout race/idempotency and room rule cases. Firestore rules Emulator suite: **1 passed**.
- Android validation: `:app:assembleDebug` and unit-test tasks for `app`, `data`, `feature:multiplayer`, `feature:friends`, `feature:chat`, and `feature:notifications` passed. New social ViewModel tests cover friend search/request/errors, direct chat/read/pagination, and notification preferences.
- No production deployment, billing change, production data access, live FCM delivery, or physical-device QA occurred. Emulator is standard edition; local Node `24.22.1` differs from the configured Node `22` Functions runtime.

## Competitive profiles, leaderboards, seasons, and tournaments (Batch 5)

- Added verified-match-only pairwise Elo rating changes, deterministic global/friend leaderboards, minimum-match win-rate eligibility, rank-safe pagination, privacy filtering, and bidirectional block filtering.
- Added owner-claim-protected season creation/activation/manual finalization plus scheduled due-season finalization. Season payouts use the existing server-owned wallet/XP ledger and are idempotent.
- Added owner-claim-protected, no-entry-fee single-elimination tournaments with capacity/deadline registration, withdrawal, rating seeding/byes, bracket publication, assigned multiplayer matches, verified-result advancement, completion notifications, and champion rewards. Bracket rounds are stored as a Firestore map of round arrays (Firestore does not support nested arrays).
- Added server-unlocked competitive achievements and in-app notifications. Android now provides the leaderboard/profile/season/tournament screen, friend rankings, registration/withdrawal, bracket view, and launch of an assigned tournament match.
- Competitive records and brackets are callable/server-owned; rules tests deny client reads/writes to competitive profiles, result records, seasonal entries, tournaments, and achievements. No new permissive rule was added.
- Functions unit tests: **22 passed**. Functions Firestore Emulator suite: **17 passed**, including rating idempotency, hidden-profile rank gaps, tournament capacity/withdrawal/match assignment/reward, and season finalization. Firestore rules Emulator suite: **1 passed**.
- Android validation: all `testDebugUnitTest` reports contain **87 tests, 0 failures/errors**; `:app:assembleDebug` succeeded.
- No production season/tournament was created or activated. No Functions/rules/indexes were deployed; no production data, billing, region, commit, or push was changed. Emulator coverage does not verify Enterprise behavior or production IAM/runtime.
