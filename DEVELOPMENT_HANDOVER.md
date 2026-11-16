# ArenaCards development handover

Updated: 2026-11-14

## Batch 6 premium visual refinement

- Shared Compose visuals now use shape/spacing tokens, subtly elevated outlined panels, custom Canvas avatars keyed by the existing profile avatar ID, and original felt-table decoration.
- Game cards use improved contrast, selected-card lift/tilt, and stable-card-ID-keyed reveal animation. Reduced motion suppresses selection transforms and bypasses flips. Online and offline game rules/state are unchanged.
- The profile picker previews the original avatar art; no profile document or avatar ID schema was changed. Home profile actions and practice controls wrap more appropriately on narrow screens.
- Added `ArenaVisualComponentsTest` instrumentation checks for card semantics/action handling, disabled state, and accessible avatar identity. The suite compiles, but its three tests failed before assertions on API 37 because Espresso could not find `InputManager.getInstance`; rerun on a supported stable API image.
- Latest Android verification after the visual updates: app/game unit tests passed (2/40), changed UI modules and Android test source compiled, and `:app:assembleDebug` succeeded. The app launched on the API 37 emulator and the narrow home/avatar presentation was visually inspected; no gameplay or accessibility certification was performed.
- Assets remain original Compose drawing; no new image/font/audio files, Firebase deployment, commit, or push.

## Latest offline-experience additions

- Settings and practice-history destinations are wired into the main app. Theme, sound-effect enablement/volume, haptics, and reduced-motion choices are stored locally with DataStore.
- Practice totals, recent results (up to 30), and milestones are device-local. Offline results do not update a server profile or grant competitive rewards.
- Card faces, card back, Wild emblem, and brand mark use original Compose drawing; Android `ToneGenerator` provides optional feedback tones. No bundled music or third-party artwork/audio has been added.
- Cards expose spoken labels and playable/disabled/selected state; status changes use a polite live region. Settings and history scroll on smaller screens.
- New card IDs are stable for deck/session persistence, and legacy sessions without card IDs are still supported.

## Latest automated checks

- Android unit tests: 87 passed across the Gradle modules; `:app:assembleDebug` passed.
- Cloud Functions unit tests: 22 passed; Functions Firestore Emulator suite: 17 passed; Firestore rules Emulator suite: 1 passed.
- These checks do not replace Compose UI, TalkBack, font-scaling, live FCM, performance, or physical-device testing. The owner's device test remains reserved for final QA.

## Work completed in the current development pass

- Reviewed the pending offline game implementation and its engine, ViewModel, Compose screen, and tests.
- Confirmed a 108-card composition: 4 colors, one zero and two each of 1–9 and the three action cards per color, plus four each of Wild and Wild Draw Four.
- Verified number turn progression at two, three, and four seats; Skip and Reverse transitions; two-player Reverse behavior; draw penalties; Wild color choice; Wild Draw Four legality; draw-pile reshuffling/exhaustion; scoring; win/draw states; CPU choices; and state restoration with unit tests.
- Fixed two concrete issues:
  - When neither draw nor discard cards were available, a player who attempted to draw could no longer play a matching card already in hand.
  - The practice UI exposed opponent hand score derived from hidden cards; it now shows only public card counts.
- Updated the practice screen, tests, and remaining-work documentation.
- Implemented the selected Wild Draw Four challenge flow, including challenge UI, challenge result/penalties, CPU challenge decisions that do not inspect hidden hands, and persisted challenge state. Draw stacking remains disabled.
- Added the original ArenaCards rule specification in [GAME_RULES.md](./GAME_RULES.md).
- Changed `GameViewModel` to expose a SavedStateHandle-only constructor for Android's saved-state-aware ViewModel factory; deterministic random injection is retained for tests.
- Two review issues were found and fixed: empty-pile draw behavior and opponent hand-score disclosure. Last-card draw penalties are now applied before a winner is finalized.

## Earlier offline validation

- `:feature:game:testDebugUnitTest`: 37 tests passed, 0 failures; rerun after final test-constructor correction.
- `:feature:auth:testDebugUnitTest`: 16 tests passed, 0 failures.
- `:feature:profile:testDebugUnitTest`: 11 tests passed, 0 failures.
- `:app:assembleDebug`: passed; rebuilt after final test-constructor correction. APK is about 18.7 MB.
- No real-device test, Firebase deployment, production integration, billing activation, or production-data change was performed.

## Batch 5 competitive implementation

- Implemented server-authoritative Elo ratings from verified multiplayer results, global/friend/season leaderboards, privacy and two-way block filtering, pagination, and win-rate eligibility after ten verified matches.
- Implemented owner-admin season lifecycle and idempotent scheduled finalization with season rewards through the existing wallet/XP ledgers.
- Implemented no-entry-fee single-elimination tournaments with capacity/deadline checks, registration/withdrawal, rating seeding/byes, bracket assignment, multiplayer match launch, verified advancement, notifications, and champion coin/XP reward.
- Competitive profile initialization/visibility synchronization, four competitive achievements, Android leaderboard/profile/season/tournament UI, Firestore index definitions, and deny-by-default rule tests were added.
- Firestore bracket persistence stores rounds in a map because Firestore rejects nested arrays. Client callable responses map the rounds back to the UI shape.
- Latest validation: 22 Functions unit tests, 17 Functions Emulator scenarios, 1 Firestore rules Emulator scenario, 87 Android unit tests, and debug APK assembly passed. Emulator is standard edition; runtime local Node is 24.22.1 vs target Node 22.
- No production deployment, tournament/season creation, billing or production setting change, commit, or push occurred. No physical-device, production IAM, Enterprise behavior, or live FCM verification has been performed.

## Git checkpoint

Previous checkpoint is on GitHub; the current challenge implementation is not yet committed or pushed:

- Previous checkpoint: `07a95504a1da395fe026770781892afd0736b16d`.
- Remote: `https://github.com/Ashok-Workshop/ArenaCards`
- Local Firebase configuration and machine-specific files remain ignored.

## Batch 3 multiplayer foundation (historical snapshot)

- Implemented server-side matchmaking for real players only, private room codes, server-dealt matches, validated game actions, reconnect/heartbeat, server-deadline timeout processing, verified match results/history, and atomic rewards/statistics.
- Android now routes signed-in players to an online lobby/table, supports private rooms and match history, observes the public match plus the caller's private hand, and waits for authoritative state rather than applying actions optimistically.
- Firestore rules deny all client writes; match documents are participant-readable, private hands are owner-and-participant readable, rooms are member-readable, and history/status are owner-readable.
- Backend tests also verify blocked room joins, stale-action rejection, timeout boundary behavior, and idempotent verified match rewards.
- Match reward values are 25 coins and 50 XP per player plus 75 coins and 50 XP for the winner. At that Batch 3 checkpoint, room settings were fixed and social features/scheduled timeouts were absent; Batch 4 below supersedes those limitations.
- Source and emulators are validated only. Nothing has been deployed or production-tested. The local emulator is standard edition and Node 24.22.1, while Functions target Node 22.

## Latest Batch 3 automated checks

- `:feature:multiplayer:testDebugUnitTest`: 4 passed; `:data:testDebugUnitTest`: 2 passed.
- `:feature:game:testDebugUnitTest`: 40 passed; `:app:testDebugUnitTest`: 2 passed; `:app:assembleDebug`: passed.
- Functions unit tests: 16 passed; Functions emulator tests: 11 passed; Firestore rules emulator test: 1 passed.
- These checks do not replace Compose UI/accessibility testing, owner physical-device QA, Enterprise/production verification, or deployment review.

## Batch 4 social/backend and Android implementation

- Added server-side Gamer ID search, symmetric friend requests, list/remove, block/unblock, presence, private-room invitations/expiry, direct and room messaging, report submission/moderator review, notification token/preferences/in-app records, and data-only FCM delivery.
- Added scheduled timeout processing with per-turn/version idempotency, timeout-count abandonment, and private-room rules for 30/45/60-second turns, challenge toggle, standard mode, and no stacking. Scheduled functions remain undeployed and inactive.
- Android now has friends/requests/blocked/invitations UI, direct/room chat with older-message paging and reporting, notification center/preferences/permission/deep-link flow, foreground presence heartbeats, and private-room friend invitations/rule controls.
- Firestore rules and indexes cover social queries while keeping client writes denied. Functions Emulator tests cover social actions, blocks, invitation expiry, chat, reports, notification preferences, and timeout-worker races.
- Fixes driven by Emulator tests: use Firestore `FieldPath` updates for per-user unread counters; avoid creating premature timeout audit keys during transaction retries; align room deadline to selected turn duration; keep invitation authorization dependent on actual friendship; and validate notification pagination with a full composite cursor.

## Next recommended work

1. Complete deferred physical-device and accessibility QA after the planned feature phases; include offline challenge handling and online multi-device/social flows.
2. Plan account deletion, data retention, moderator-claim provisioning, App Check, monitoring, IAM, and scheduled-job operations before any deployment review.
3. Keep AdMob SSV, coin spending, and full administrative tooling as separate future feature work; finish manual privacy/accessibility and competitive UI/device QA.
4. Keep Firebase deployment, Cloud Scheduler activation, billing changes, production data access, commit, and push pending explicit owner approval.

The latest Batch 5 Android build/unit tests, Functions unit/emulator suites, and Firestore rules Emulator suite have passed. The worktree remains uncommitted; no changes have been pushed. Emulator results are not production verification.

## Reference documents

- [MASTER_PROJECT_CONTEXT.md](./MASTER_PROJECT_CONTEXT.md)
- [MASTER_BUILD_STATUS.md](./MASTER_BUILD_STATUS.md)
- [REMAINING_WORK.md](./REMAINING_WORK.md)
- [PENDING_MANUAL_ACTIONS.md](./PENDING_MANUAL_ACTIONS.md)
