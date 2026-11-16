# ArenaCards remaining work

Updated: 2026-11-16

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

- The following local polish is implemented: persisted sound/haptic/reduced-motion/theme settings, generated feedback tones, responsive scroll surfaces for settings/history, original Compose-drawn cards/branding, accessible card states/live game status, local practice totals/history, and noncompetitive milestones.
- Batch 6 adds shared visual tokens, richer card faces/treatment, Canvas-drawn avatar identities, table backdrop and flip reveal, tighter home/practice layout behavior, and initial Compose instrumentation checks.
- Remaining polish includes visual screenshot review, TalkBack/switch-access audits, contrast and font-scaling checks, animation-performance profiling, orientation/background/process-death checks, and owner physical-device gameplay QA. Defer personal-device testing until final QA after planned offline and online feature phases.
- Saved sessions now preserve stable card IDs while continuing to decode legacy sessions. Validate saved-game behavior across app upgrades and force-stop scenarios during final device QA.
- Wild Draw Four challenge play is implemented, persisted, and covered by unit tests; draw-card stacking is disabled. Physical-device challenge QA, match-wide score targets, cumulative match points, house-rule configuration, and a challenge explanation/tutorial remain future decisions/work.
- Extend UI/state tests during implementation; current unit tests cover engine transitions and saved-state restoration, not the full Compose experience. Perform end-to-end gameplay QA on the owner's phone in the final phase.
- Preserve the rule that offline matches cannot grant competitive currency or results.

### 2. Extend the trusted backend and social experience

- Profile, wallet/rewards, and server-authoritative multiplayer source exists with emulator coverage. It is not deployed; production behavior, IAM, App Check enforcement, scaling, and operational procedures are unverified.
- Batch 4 adds Gamer ID friends/requests, block/unblock, heartbeat presence, room invitations, direct/room chat, server rate limits, reports/moderator review, notification preferences/FCM/in-app records, configurable private-room rules, scheduled turn timeout processing, and invitation expiry. Android source includes social/chat/notification UI and room-rule/invite controls.
- The social and scheduled-job work remains undeployed. Production behavior/IAM, FCM delivery, App Check enforcement, retention/deletion policy, moderator claim provisioning, abuse/load testing, scaling, and operational procedures are unverified.
- Keep every competitive record, timeout transition, moderation decision, and reward server-authoritative. The simple content filter is not full moderation; presence can be stale during force-stop/network loss; there are no E2E-encrypted chats or reliable delivery receipts.
- Use server-enforced roles and audit logs before building administrative tools.

### 3. Remaining requested feature areas

- **Coins and daily rewards:** wallet, transaction ledger, UTC streaks, missions, claims, rate limits, and duplicate/replay protection are implemented in source and emulator-tested. Coin spending and production deployment are not implemented.
- **AdMob:** rewarded ads, server-side verification, test/production separation, and disclosures are not implemented.
- **Online multiplayer:** human matchmaking, private rooms, server-authoritative state/actions, reconnect/heartbeat, scheduled and client-requested server-deadline timeout handling, verified history/rewards, configurable room rules, and social invitations/chat are in source and emulator-tested. Deployment, real-device QA, and load/cost testing remain.
- **Friends/social:** Gamer ID lookup, requests, friend lists, block/unblock, presence display, room invites/acceptance, and reports are implemented in source. Moderator operations require owner-provisioned trusted claims; production abuse and retention policies remain.
- **Chat:** direct and room messaging, server writes, read state, page-based history, block checks, rate limits, message reporting, and notification fallback are implemented in source. E2E encryption, reliable delivery receipts, production retention/deletion, and human moderation operations remain.
- **Leaderboards/tournaments:** Batch 5 implements server-owned verified Elo ratings, global/friend/season leaderboards, privacy/block filtering, season lifecycle/rewards, and no-entry-fee single-elimination tournaments with brackets, assigned matches, and champion rewards. Android leaderboard/profile/tournament entry UI and emulator coverage are present. Production deployment, admin-claim operations, physical-device/privacy/accessibility QA, and load/cost testing remain.
- **Progression:** local practice milestones, server-side daily streak achievements/XP, and four competitive achievements exist. Broader durable missions, coin spending, and additional badge/content design remain future work.
- **Push notifications:** FCM data-only delivery, device permission/token registration, preferences, deep links, and in-app notification center are implemented in source. Live FCM verification and token cleanup/account-deletion policy remain.
- **Admin/moderation:** secure player/report/match/wallet/transaction operations, server roles, and audit UI are not implemented.

### 4. Firebase deployment readiness

- Current backend source includes profile, wallet/reward, multiplayer, social/chat/moderation, notifications, competitive rankings/seasons/tournaments, and scheduled processing. Full admin tooling and App Check enforcement remain incomplete.
- Confirm Firebase project `arenacards-f3279`, package `com.arenacards.game`, provider settings, Firestore creation/location, and the `us-central1` Functions region in Firebase Console.
- Configure and verify App Check, quotas, monitoring, budget alerts, and abuse/rate controls before production rollout.
- Deploy nothing and enable no paid services without explicit owner approval.

### 5. Release, costs, and policy readiness

- Release signing, AAB generation verification, R8/ProGuard validation, app icon/branding review, privacy policy, Google Play Data safety, account deletion, ad disclosure, age rating, user-generated-content policies, store listing, screenshots, and release notes are not complete.
- No monetary cost estimate is claimed. Estimate Firebase, SMS, Functions, Firestore, App Check, ads, multiplayer, chat, and notifications only after region, user volume, data shape, and traffic assumptions are agreed; verify current vendor pricing and quotas before launch. The `COST_ESTIMATION.md` document lists cost drivers.
- Never commit signing keys/passwords, Firebase service-account credentials, local `google-services.json`, or local machine configuration.

## Latest development pass (2026-11-16)

- The practice pause menu now exposes Easy, Medium, and Hard CPU difficulty choices and reflects the active selection. The choice continues to use the existing SavedStateHandle persistence.
- Hard CPU card selection now gives stronger preference to shedding high-point action cards while conserving Wild and Wild Draw Four cards when a useful colored action is available. A focused unit test covers this decision.
- The home hero's continuously animated card fan now has a static reduced-motion rendering, and the existing settings preference is passed from the app navigation host.
- Leaderboard/tournament, chat, profile, wallet, and social screen presentation received incremental branded-header, podium/bracket, contrast, and state-transition refinements. The card-back accessibility test was extended.
- These are source changes on `rebuild/phase-1-audit`; no fresh Gradle build or test run was available in this pass. Do not mark the new CPU test or UI test as executed until a supported Android build environment runs them.
- The branch is intentionally not merged. It has diverged from `main`; reconcile the three base-only commits before preparing a merge.


### Latest automation pass (2026-11-16)

- Added `.github/workflows/android-ci.yml` on `rebuild/phase-1-audit`. Pull requests targeting `main`, pushes to `main` and `rebuild/**`, and manual dispatch now run JDK 17 setup, Gradle unit tests, and a debug APK assembly.
- The workflow creates a temporary placeholder `google-services.json` only when the file is absent, to satisfy the Google Services Gradle plugin during CI. It contains no real credentials and does not connect to Firebase or deploy anything.
- The workflow has been committed but has not yet run successfully on GitHub; its first run is the validation gate. It does not replace emulator, instrumentation, physical-device, security, or production QA.
- Branch comparison against `main` reports 52 commits ahead and 3 behind with divergent histories. No merge/rebase was attempted; inspect the three base-only commits and overlapping files before reconciling.

## Validation status

- Latest `:feature:game:testDebugUnitTest`: 40 tests passed, including card-ID uniqueness, legacy saved-session decoding, completed-match recording, and practice-achievement rules.
- Latest `:app:testDebugUnitTest`: 2 tests passed, covering DataStore preference writing and duplicate practice-match handling.
- Latest `:app:assembleDebug`: passed after settings/history navigation, local persistence, graphics, and accessibility changes.
- Latest multiplayer ViewModel tests: 4 passed. Functions unit tests: 16 passed; Functions emulator tests: 11 passed; rules emulator test: 1 passed. Android data tests: 2 passed; app persistence tests: 2 passed; final game tests: 40 passed; debug assembly passed.
- Batch 4: app debug assembly and unit tests for `app`, `data`, `feature:multiplayer`, `feature:friends`, `feature:chat`, and `feature:notifications` passed. Functions unit tests (16), Functions Emulator scenarios (16), and Firestore rules Emulator scenario (1) passed.
- `:feature:game:testDebugUnitTest`: passed after challenge implementation; 37 engine and ViewModel tests, 0 failures. Repassed after the final test-constructor correction.
- `:feature:auth:testDebugUnitTest`: passed (16 tests; no auth source changes in this iteration).
- `:feature:profile:testDebugUnitTest`: passed (11 tests; no profile source changes in this iteration).
- `:app:assembleDebug`: passed after game source/UI changes and again after the final test-constructor correction.
- Emulator testing used standard Firestore Emulator and local Node `24.22.1`; Functions target Node `22`. These tests do not establish Enterprise or production behavior.
- No real-device playtest or production Firebase integration test was performed.
- Social/FCM/room-rule emulator tests do not verify production delivery, Enterprise Firestore, scheduled-job activation, physical-device UX, or App Check.
