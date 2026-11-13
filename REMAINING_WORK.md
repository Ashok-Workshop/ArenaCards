# ArenaCards remaining work

Updated: 2026-11-13

This tracker reflects the repository after extending offline practice. “Implemented” means source and tests exist; it does not imply production Firebase deployment, real-device verification, or Play Store readiness.

## Completed foundations

- Android app, modular Kotlin/Compose architecture, Material 3 design system, onboarding, dashboard, navigation, and Firebase Authentication source are present.
- Firebase UID-based profiles, username/Gamer ID Cloud Functions source, profile UI, and restrictive Firestore rules are present.
- Offline practice now has the 108-card deck, two-to-four seats, color/value matching, skip, reverse, draw penalties, wild color choice, Wild Draw Four color validation, winner detection, discard reshuffling, stalemate handling, three CPU difficulty levels, pause/resume, and `SavedStateHandle` match restoration.
- Review found and fixed an exhausted-pile turn bug so players may still play a matching card from hand; removed display of opponent hand score derived from hidden cards.
- The defensive source review found no actionable vulnerabilities in the reviewed code and tests. No deployed Firebase environment or production configuration was verified.
- Firebase Functions and Firestore rules have not been deployed. No billing or production services were enabled.

## Prioritized remaining work

### 1. Complete offline-game polish and verification

- Add card/player animations, sound effects and accessibility/visual QA on physical devices and different screen sizes.
- Add durable local match statistics, configurable game settings beyond CPU difficulty, and validate saved-game behavior across app upgrades and force-stop scenarios.
- Decide and document the intended rules variant for edge cases such as Wild Draw Four challenge rules and scoring across multiple rounds.
- Extend UI/state tests and perform gameplay QA; current unit tests cover engine transitions and saved-state restoration, not the full Compose experience.
- Preserve the rule that offline matches cannot grant competitive currency or results.

### 2. Establish trusted backend capabilities before competitive/social clients

- Review and deploy the existing profile Functions/rules only after owner approval and project/App Check configuration.
- Implement server-authoritative match state and result verification before matchmaking, competitive leaderboards, or match rewards.
- Add server-owned wallet/ledger, idempotent rewards/missions, limits, and emulator tests before creating any client wallet UI.
- Define privacy, retention, moderation, blocking/reporting, abuse/rate-limit, and account deletion policies before chat/social launch.
- Use server-enforced roles and audit logs before building administrative tools.

### 3. Remaining requested feature areas

- **Coins and daily rewards:** wallet, transaction ledger, streaks, missions, claims, limits, and duplicate/replay protection are not implemented.
- **AdMob:** rewarded ads, server-side verification, test/production separation, and disclosures are not implemented.
- **Online multiplayer:** matchmaking, private rooms, invitations, authoritative turn/card state, reconnects, timeouts, results, and scale/cost testing are not implemented.
- **Friends/social:** search, requests, lists, presence, invitations, blocking, and reports are not implemented.
- **Chat:** private/room messaging, delivery/read state, reporting, rate limits, moderation, and retention are not implemented.
- **Leaderboards/tournaments:** verified rankings, history, listings, registration, brackets, and progression are not implemented.
- **Progression:** achievements, badges, milestones, durable missions, and server-controlled lifetime statistics are not implemented beyond current profile display fields.
- **Push notifications:** FCM delivery, preferences, permission UX, and notification test coverage are not implemented.
- **Admin/moderation:** secure player/report/match/wallet/transaction operations, server roles, and audit UI are not implemented.

### 4. Firebase deployment readiness

- Current backend source only implements profile and username/Gamer ID operations. Wallet, rewards, matches, friends, chat, leaderboards, administration, and App Check enforcement still need design, implementation, and tests.
- Confirm Firebase project `arenacards-f3279`, package `com.arenacards.game`, provider settings, Firestore creation/location, and the `us-central1` Functions region in Firebase Console.
- Configure and verify App Check, quotas, monitoring, budget alerts, and abuse/rate controls before production rollout.
- Deploy nothing and enable no paid services without explicit owner approval.

### 5. Release, costs, and policy readiness

- Release signing, AAB generation verification, R8/ProGuard validation, app icon/branding review, privacy policy, Google Play Data safety, account deletion, ad disclosure, age rating, user-generated-content policies, store listing, screenshots, and release notes are not complete.
- No production cost estimate is included. Estimate Firebase, SMS, Functions, Firestore, App Check, ads, multiplayer, chat, and notifications only after region, user volume, data shape, and traffic assumptions are agreed; verify current vendor pricing and quotas before launch.
- Never commit signing keys/passwords, Firebase service-account credentials, local `google-services.json`, or local machine configuration.

## Validation status

- `:feature:game:testDebugUnitTest`: passed after game review/fixes; 28 deterministic engine and ViewModel tests.
- `:feature:auth:testDebugUnitTest`: passed (16 tests; no auth source changes in this iteration).
- `:feature:profile:testDebugUnitTest`: passed (11 tests; no profile source changes in this iteration).
- `:app:assembleDebug`: passed after game source/UI changes.
- Firebase Functions helper and Firestore Emulator rules tests were previously passing; backend source was not changed in this iteration.
- No real-device playtest or production Firebase integration test was performed.
