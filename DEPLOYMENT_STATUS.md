# ArenaCards deployment status

Updated: 2026-11-14

## Firebase target

- Project: `arenacards-f3279`
- Firestore database ID: `unoashok`
- Owner-reported Firestore location/edition: `asia-south1`, Enterprise, Firestore Native
- Functions region: `us-central1` (retained; no region migration is being made)
- Android Firebase BoM: `33.7.0` (Firestore Android SDK `25.1.1`, callable Functions SDK `21.1.0`); Functions runtime Node.js `22`, `firebase-functions` `7.4.0`, `@google-cloud/firestore` `9.4.1`; Firebase CLI `15.36.0`
- Android Google Services config: local and ignored by Git; it identifies the Firebase project but does not select a Firestore database.

Android and Functions source now explicitly select the named database `unoashok`. The Firebase CLI config associates the restrictive rules with that database. No `(default)` database is configured or created by this change. Local emulator coverage used the standard Firestore Emulator; it does not verify Enterprise-only features.

## Deployment and verification

- Cloud Functions: not deployed.
- Firestore rules: not deployed.
- Production database/data: not accessed or changed.
- Billing: not changed or enabled.
- Production IAM, live connectivity, and deployed function region: not verified.
- The runtime Firestore client uses Application Default Credentials; no service-account keys are included.

Deployment remains blocked pending explicit owner approval and Console verification of the database/project, runtime IAM access, current rules, quotas, App Check plan, monitoring, and rollback procedure. Do not infer production readiness from local tests.

The existing callable endpoint region stays `us-central1`. Moving to `asia-south1` would require changing each callable deployment and the Android Functions client together, coordinating old app versions and endpoint migration, and obtaining separate approval.

## Wallet/rewards source readiness

- Source now includes callable endpoints for account snapshots, daily rewards, mission claims, and paginated coin-ledger history, all using the explicitly configured `arenacards-f3279` / `unoashok` Firestore client in `us-central1`.
- The Android wallet UI is wired to callable Functions. Firestore rules deny client access to the new wallet, claim, mission, progression, and rate-limit collections.
- Local validation passed: 11 Functions unit tests, 4 Functions Firestore Emulator scenarios, 1 Firestore rules Emulator scenario, 3 wallet ViewModel tests, 2 app unit tests, and debug APK assembly. The Emulator is standard edition; the local Node version is `24.22.1` rather than deployed runtime Node `22`.
- No Firebase Functions/rules deployment, production connection test, data migration, production write, billing change, or AdMob setup occurred. App Check enforcement remains off until Android provider and Console configuration are ready. Online-match rewards now exist in source only and require the separate review/approval described below; AdMob SSV remains deferred.

## Multiplayer source readiness (Batch 3)

- Cloud Functions source adds human-only matchmaking, private room lifecycle, server-owned real-time matches/actions, reconnect/heartbeat, turn-expiry processing, verified match history, and atomic match rewards/statistics. Android online lobby, room/table, action, and history flows are wired to these callables and Firestore listeners.
- The source targets `arenacards-f3279` / `unoashok` and stays in `us-central1`. No deployment, region migration, data migration, billing change, production test, or production write occurred.
- Emulator coverage: blocked room joins, private-room authorization/readiness, queue pairing/cancellation/idempotency, hidden hand isolation, stale-action rejection, server deadline handling, verified reward-once behavior, and match history. It does not validate production IAM, Enterprise behavior, scale, or latency.
- Production approval must account for disabled App Check enforcement and the absence of production load/cost testing. The room rules and scheduled timeout processing described below are source-only.
- Functions region change to `asia-south1` remains a separate decision requiring coordinated callable-client migration and old-client compatibility planning. No silent migration was made.

## Social, chat, notifications, and configurable-room source readiness (Batch 4)

- Cloud Functions source adds friend requests/lists, blocks, presence, private-room invitations, direct and room chat, reporting/moderator review, notification tokens/preferences/in-app records, and scheduled invitation expiry.
- Scheduled multiplayer timeout processing runs once per minute in source and applies server-deadline transitions with idempotent audit records; repeated timeouts abandon a match without winner rewards. It is not deployed or active and may require Cloud Scheduler/billing configuration after owner review.
- Private rooms support standard rules with 30-, 45-, or 60-second turns, Wild Draw Four challenge enabled/disabled, and stacking disabled. Owners can change rules only while the room is waiting. Android exposes those controls.
- Android now has friends/requests/blocked/invitations, direct and room chat, message pagination/reporting, notification center/preferences, FCM permission/token handling, deep-link routing, and presence heartbeats.
- Firestore indexes cover the new query shapes; rules remain deny-by-default for client writes and scope social reads to account owners, participants, room members, or report owners. FCM payloads exclude message text.
- Functions unit tests (16), Functions Emulator scenarios (16), and Firestore rules Emulator scenario (1) passed. Android assembly and relevant module unit tests passed. These checks do not prove deployed or Enterprise production behavior.
- Nothing in Batch 4 has been deployed. Live FCM delivery, production moderator claim setup, scheduled jobs, and Android device behavior remain unverified. Keep the existing `us-central1` Functions region; no region migration is included.

## Competitive profiles, seasons, and tournaments (Batch 5)

- Cloud Functions source adds verified-result Elo updates, global/friend/season rankings, owner-admin season lifecycle, hourly due-season finalization, tournament registration/withdrawal, single-elimination brackets, assigned multiplayer matches, competitive achievements, in-app notifications, and server-owned rewards.
- Android has the leaderboard/profile/season/tournament entry screen and callable repository. New indexes cover competitive ranking, season ranking/finalization, and lifecycle queries; rules remain deny-by-default for competitive data.
- Functions unit tests (22), Functions Emulator scenarios (17), Firestore/Auth rules Emulator scenario (1), 87 Android unit tests, and debug APK assembly passed. The local emulator is standard edition and cannot verify the owner's Enterprise database behavior.
- No season or tournament was created in production; no Functions, rules, or indexes were deployed. No production data, billing, or deployed region changed. Admin claim provisioning and production IAM remain owner actions.
- Before approval, review rating/rank policy, tournament champion prize values, admin-claim provisioning, schedules/retries/monitoring, database indexes, privacy behavior, and rollback. Physical-device, load/scale, cross-region latency/cost, Enterprise, and production tests remain outstanding.
