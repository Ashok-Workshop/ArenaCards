# ArenaCards pending manual actions

Updated: 2026-11-18

These are owner actions. No Firebase deployment, billing activation, or production-data changes have been performed.


## Latest automated validation (2026-11-16)

- Android GitHub Actions run on commit `4138c44` passed unit tests and debug APK assembly after lifecycle-aware Flow collection was added to the app root, game, auth, profile, and leaderboard screens.
- Firebase Functions CI passed on commit `ae11798`: Node.js unit tests, Firestore rules emulator tests, and Functions emulator tests. The suite includes moderator/admin report review authorization, report idempotency, self-report/invalid-reason rejection, and report rate-limit coverage.
- These CI checks are source-level validation only. They do not establish production Firebase deployment, live FCM delivery, physical-device QA, accessibility certification, or Play Store readiness.

- The latest confirmed Android CI pass is commit `8a3d7b2` (unit tests and debug APK assembly). Commit `050fd47` adds two Compose instrumentation tests for the card-reveal animation and reduced-motion path; GitHub has not reported a check run for that commit yet, so those new tests remain unverified. The earlier API 35 component suite passed, but do not treat that as execution of the new tests.

- Android CI passed on commit `8977687` after retrying a transient Maven Central HTTP 429 dependency-download failure. The run completed unit tests, debug APK assembly, and `assembleDebugAndroidTest`; the Compose instrumentation source compiles. This did not execute the UI tests on an emulator.
- Commit `52b5519` adds an API 35 emulator step using `connectedDebugAndroidTest`. Its result is pending; only record UI test execution after the emulator job completes successfully.

- API 35 emulator instrumentation attempt on commit `f139828` built the app and test APKs, but the app instrumentation process crashed before discovering or executing tests (reported 0 tests). A prior attempt also failed at emulator boot. This is not a passing UI test result; capture device logcat/test report and diagnose runner startup before treating the suite as verified.
- The CI command is now scoped to `:app:connectedDebugAndroidTest` to avoid invoking connected instrumentation tasks across every feature module. The latest run result should be checked before release.

## Before building from a fresh clone

- Install Android Studio and the project-required Android SDK/platform tools.
- Provide the Firebase Android configuration file locally at `app/google-services.json`. It is intentionally excluded from Git; do not commit it.
- Configure local SDK paths in `local.properties` as appropriate for the machine; this file is ignored.

## Firebase Console and production verification

- In Firebase project `arenacards-f3279`, confirm Authentication providers (Google, Email/Password, Phone), email verification behavior, Google OAuth/SHA fingerprints, phone SMS region policy, quotas, and test numbers.
- The owner reports that the only Firestore database is `unoashok` in `asia-south1`. Android and Functions source now explicitly select `arenacards-f3279` / `unoashok`; verify this configuration against the Console before any production deployment. Do not create a `(default)` database.
- Callable Functions remain in `us-central1` to avoid an unapproved endpoint migration. Decide on a coordinated region migration to `asia-south1` separately; update Functions and Android callable clients together and plan old-endpoint compatibility before deploying.
- Review username moderation and Cloud Functions source, App Check setup/enforcement plan, Firestore rules, quotas, monitoring, abuse controls, and backup/recovery expectations.
- Cloud Functions and Firestore rules are not deployed. Deployment, production verification, and any billing upgrade require separate explicit approval. Do not deploy from local testing.
- After approved deployment, test new/existing profiles and auth flows using dedicated test accounts; verify denied cross-user reads/writes and account deletion/recovery requirements.
- Before deploying wallet/reward functions and rules, review the new server-owned wallet/ledger, UTC reward schedule, mission/progression/achievement schema, rate limits, callable authorization, and expected operational support/refund policy. No data migration is included.
- Decide and configure Android App Check provider(s) and Firebase Console registration before setting `ARENA_ENFORCE_APP_CHECK=true`; current enforcement defaults off.
- Batch 5 competitive source is implemented but undeployed. Before any approval, review Elo/ranking and minimum-win-rate policy, season calendar/rewards, tournament capacity/deadlines/champion reward policy, and Firestore composite indexes. Do not create/activate a production season or tournament without explicit owner approval.
- Provision the `admin` custom claim through an owner-controlled secure process before invoking season/tournament administration. Confirm least privilege, audit/rotation, and recovery; no client can assign claims.
- Multiplayer, social features, verified match rewards/history, and private-room rules now exist in source and emulator tests only; none is deployed or production-verified. Review reward constants, 30/45/60-second room settings, Wild Draw Four challenge toggle, no-stacking rule, block/report behavior, moderator claim policy, chat retention/filtering, FCM payloads, and server-only state model before approving deployment.
- Decide and document retention/deletion handling for messages, reports, notification tokens, presence, invitations, moderation audit, and scheduled timeout records. Provision trusted moderator/admin custom claims through a secure owner-controlled process; no client role management exists.
- Review Cloud Scheduler/invitation-expiry job rollout, runtime IAM, quotas, monitoring, failure alerts, retry/backfill, and any Cloud Scheduler/billing prerequisites before deployment. Scheduled timeout and invitation processing are source-only and not active.
- Configure and verify Android push delivery, notification channels and permission behavior, token cleanup on sign-out/account deletion, and in-app notification fallback using approved test accounts. Live push delivery has not been tested.
- Verify the actual deployed Functions runtime uses Node.js 22; local validation used Node `24.22.1`. Review runtime IAM access, cross-region latency/egress, quotas, budgets, monitoring, backup/recovery, and rollback before approval.

## Device and release QA

- Owner preference: defer installing/testing on a personal mobile device until final QA, after the planned offline and online feature phases are implemented. Continue automated tests and debug builds during development.
- Continue the in-progress app-wide visual pass using the screen inventory in `PREMIUM_EXPERIENCE_AUDIT.md`. Only onboarding, guest home, offline setup, practice table, pause, and exit confirmation were inspected on the API 35 emulator; authenticated/profile, wallet, settings, social, notification, leaderboard, and tournament flows were not visually audited in this pass.
- The Compose component suite now passes all four tests on API 35, including drag-to-discard. API 37 previously failed inside Espresso setup before assertions; do not record that run as a UI test result.
- Validate the UNO announcement/catch flow on-device: a player has ten seconds to announce after reaching one card, another participant may catch a missed call, and the offender draws up to two cards. Confirm timer behavior across pause/resume and process restoration; online behavior remains source-only until explicitly approved deployment.
- Specifically validate Wild Draw Four challenge flows and disabled stacking in physical-device gameplay.
- Review the Batch 6 card faces/avatar artwork in both light and dark themes; check large font scale, TalkBack labels, reduced-motion behavior, touch targets, and narrow-device layouts. Compose instrumentation verifies component semantics/actions only; it is not a visual or accessibility certification.
- Re-run `:app:connectedDebugAndroidTest` after future UI changes on the API 35 AVD or another supported stable image. The API 37 image remains incompatible with Espresso initialization (`InputManager.getInstance` is missing).
- Profile card/table animations and memory/battery behavior on representative low-, mid-, and high-tier devices before release. No frame-time or battery result has been measured.
- Test the debug APK on physical Android devices and different screen sizes: two/three/four-player practice, special cards, exhausted draw/discard piles, pause/resume, process death/restoration, wild color picker, accessibility, and orientation/background transitions.
- Also verify the new local settings/history flow on narrow screens, large font sizes, TalkBack, reduced motion, system haptic/audio settings, and both light/dark themes. Generated feedback tones are intentionally minimal and do not include background music.
- Real Google, email, phone-SMS, and production profile flows have not been verified by this work.
- Final online QA should cover matchmaking between multiple physical devices, cancellation/reconnect, private room codes, block denial, every action/challenge, deadline/timeout, verified result/reward/history, backgrounding, and lost connectivity. Coordinate test accounts and an owner-approved deployment/emulator setup; do not use production until explicitly approved.
- Add physical-device QA for Gamer ID search/friend requests, invitation acceptance, direct/room chat pagination/reporting, notification permission/deep links, presence transitions, and private-room rule changes. Current Batch 4 coverage is build/unit/emulator only.
- Add physical-device and multi-account QA for global/friend/season leaderboard privacy, registration/withdrawal, bracket visibility, assigned tournament matchmaking, completed placement, and notifications. No live season or tournament exists.
- Before any Play release, owner decisions/configuration are still needed for release signing, privacy policy, data-safety declarations, account deletion, age rating, user-generated content policies, advertising disclosure, listing assets, and release approval.
- Never share or commit signing keys/passwords or Firebase service-account credentials.
