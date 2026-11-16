# ArenaCards testing status

Updated: 2026-11-14

## UNO announcement and catch validation

- Cloud Functions unit tests: **23 passed**; focused multiplayer engine suite: **6 passed**, including caller/catcher authorization, the two-card penalty, deadline expiry, public snapshot fields, and next-player action closure.
- Android unit tests: **42 game, 5 multiplayer, and 2 data tests passed**. The app debug assembly and Android-test Kotlin compilation passed.
- API 35 Compose instrumentation: **4 passed, 0 failures/errors**.
- Firestore Emulator multiplayer service suite: **9 passed**, including a transaction-level UNO call/catch test that checks authorization and published pending-call state.
- The full Functions Emulator suite reported **17 passed, 1 failed**: the competitive leaderboard test expected 3 results but received 4. That competitive test passed when rerun alone (**1/1**), and the isolated multiplayer suite passed (**9/9**), indicating a combined-suite interaction rather than a reproducible UNO or competitive-service failure. No competitive code was changed.
- Emulator testing used local standard Firestore Emulator behavior only; it is not production or Enterprise verification. The UNO UI flow still needs visual/device QA.

## Batch 6.1 interaction and screen verification

- Latest command passed: `:app:assembleDebug :app:testDebugUnitTest :feature:game:testDebugUnitTest :feature:multiplayer:testDebugUnitTest :app:connectedDebugAndroidTest`.
- All four Compose instrumentation tests passed on a newly created API 35 emulator, including card semantics, disabled-card semantics, avatar identity, and drag-to-discard.
- Visual inspection covered onboarding, guest home, offline setup, practice table, pause menu, and leave-match confirmation. Screenshots are stored in the session files directory; the home card artwork was adjusted after noticing the narrow decorative Wild card text was clipped.
- This is a focused UI check, not a full screen-by-screen visual audit or certification. No online Firebase flow, authenticated profile, social screen, TalkBack, font scaling, physical-device, memory, or frame-time QA was performed.

## Batch 6 Compose visual checks

- Added Android Compose instrumentation coverage for selected yellow-card labels and click handling, disabled-card accessibility state, and accessible avatar identity.
- `:app:compileDebugAndroidTestKotlin` passed. `:app:connectedDebugAndroidTest` ran all three tests on `Medium_Phone_API_37.0`, but each failed before test assertions while Espresso initialized input injection: `NoSuchMethodException: android.hardware.input.InputManager.getInstance`. This API 37 emulator/runtime mismatch means these are **not verified UI assertions**; rerun the suite on a supported stable Android API image.
- `:app:assembleDebug`, `:app:testDebugUnitTest` (2 tests), and `:feature:game:testDebugUnitTest` (40 tests) passed after the visual changes. Changed home/profile/game/multiplayer/leaderboard/friends modules compiled.
- Installed/launched the debug build on the API 37 emulator and visually inspected the home screen and original avatar rendering. This was not a gameplay, accessibility, or physical-device certification.
- The tests do not replace TalkBack/switch-access, font-scaling/contrast, reduced-motion system-setting, physical-device, memory, or frame-time evaluation.

## Latest automated Android validation

Command:

```powershell
.\gradlew.bat :feature:game:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

Result: **BUILD SUCCESSFUL**.

- `:feature:game:testDebugUnitTest`: 40 tests passed. Coverage includes game rules/state, CPU decisions, saved-session restoration and legacy decoding, card ID uniqueness, completed-match recording, and practice-achievement thresholds.
- `:app:testDebugUnitTest`: 2 tests passed. Coverage includes settings written/read through Preferences DataStore and idempotent local practice-match recording.
- `:app:assembleDebug`: passed.

## Previously validated Firebase boundaries

- Android profile repository and Firestore named-database configuration tests passed for `arenacards-f3279` / `unoashok`.
- Cloud Functions helper/domain tests, callable profile emulator flows, and named-database Firestore/Auth Emulator rule tests passed in the prior Firebase change.
- See [MASTER_BUILD_STATUS.md](./MASTER_BUILD_STATUS.md) and [SECURITY_STATUS.md](./SECURITY_STATUS.md) for exact prior test boundaries and production limitations.

## Latest Batch 4 validation

- Android `:app:assembleDebug` passed. Unit-test tasks passed for `app`, `data`, `feature:multiplayer`, `feature:friends`, `feature:chat`, and `feature:notifications`.
- New Android tests cover friend Gamer ID search/request/error presentation, direct-message send/read behavior and older-message pagination, and notification preference persistence. Existing multiplayer and offline tests remain passing.
- Cloud Functions unit/configuration tests: **16 passed**.
- Combined Functions Firestore Emulator suite: **16 passed**, covering profile, wallet, matchmaking, private room rules, timeout worker races/idempotency/abandonment, friends/blocks, invitations/expiry, chat/reporting/preferences, and presence.
- Firestore rules Emulator suite: **1 passed**, including social read restrictions and client-write denials.
- The Emulator is standard edition; local Node is `24.22.1`, not the configured Functions runtime `22`. ADC metadata lookup warnings occur in the local environment. These results are not production verification.

## Latest Batch 5 competitive validation

- Cloud Functions unit tests: **22 passed**.
- Combined Functions Firestore Emulator suite: **17 passed**, including competitive profile initialization, admin authorization, season creation/activation/finalization, tournament capacity/duplicate registration/withdrawal, bracket assignment, multiplayer tournament match creation, champion reward calculation, duplicate completion rejection, leaderboard privacy/rank gaps, and prior profile/wallet/social/multiplayer regressions.
- Firestore/Auth rules Emulator suite: **1 passed**, including direct read/write denial for competitive profile, result, season-entry, tournament, and achievement data.
- Android `testDebugUnitTest`: **87 tests passed, 0 failures/errors** across 17 report files; `:app:assembleDebug` succeeded.
- The emulator is standard edition and local Node is `24.22.1` rather than configured Functions Node `22`; local ADC metadata warnings are not production verification.

## Still unverified

- No Compose UI/instrumentation test suite yet verifies navigation, responsive layouts, room settings, chat, notification screens, or dialogs. The initial Batch 6 component suite compiles but cannot run on the currently configured API 37 image because of the Espresso runtime mismatch documented above.
- TalkBack, switch access, font scaling, contrast, screen-reader announcements, reduced-motion behavior, and haptic/audio system-setting interactions have not been manually audited.
- Physical-device gameplay and social testing, notification permission/deep-link behavior, orientation/background/process-death flows, low-end performance, memory, battery, and thermal behavior remain pending; the owner reserved device testing for final QA.
- No live FCM delivery, Firebase production connectivity, IAM, Enterprise-specific behavior, deployment, or production-data test was performed.
- No Play release/signing/AAB validation was performed.
- Online multiplayer/social has no Compose instrumentation, load/scale or latency test, production connectivity test, App Check enforcement test, or physical-device multi-player/social test. Scheduled timeout/invitation jobs are source-only and not active.
