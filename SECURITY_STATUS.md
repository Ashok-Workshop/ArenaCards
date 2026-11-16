# ArenaCards security status

Updated: 2026-11-14

## Current backend configuration

- Firebase project: `arenacards-f3279`
- Firestore database: `unoashok` (owner-reported location: `asia-south1`)
- Android Firestore: explicitly selects `unoashok` and checks the configured Firebase project ID.
- Cloud Functions Firestore: uses `@google-cloud/firestore` with explicit project and database IDs and runtime Application Default Credentials. The old Firebase Admin `14.5.1` dependency/initialization was removed; the named-database preview API and service-account keys are not used.
- Callable Functions remain in `us-central1`; this is intentionally unchanged to avoid a production endpoint migration.
- Firebase CLI rules configuration targets `unoashok`. No production rules or functions have been deployed.

## Existing source controls

- Firestore rules deny client writes to user profiles, public projections, usernames, and Gamer ID reservations; private reads are owner-only and public reads are visibility/projection constrained.
- Profile creation and edits remain callable-backend operations. Username and Gamer ID reservations use transactions. Password-provider profile writes require verified email.
- Backend configuration tests assert the selected project, database, and region. Standard Firestore/Auth Emulator tests cover named-database rules and profile callable flows; this does not verify Enterprise-only production behavior.
- Firebase Admin SDK named-database preview APIs are not used. No service-account keys or OAuth values were added.
- New game preferences and practice statistics are stored locally using DataStore and are not sent to Firebase. Practice history is not a trusted competitive record and does not grant currency or online progression.

## Verification limits and follow-up

- Local source and emulator checks do not prove the production database exists, that production IAM grants the Functions runtime account access, or that deployment configuration is correct.
- Rules, callable Functions, Firebase settings, production data, billing, and backups have not been changed.
- App Check enforcement, rate limiting, operational monitoring, backup/recovery procedures, account deletion, and production abuse testing remain unfinished.
- Local settings/history behavior has unit coverage, but Android data-at-rest, backup/restore, device-sharing, and account-deletion retention behavior have not been independently reviewed; local practice history currently remains on the device.
- Review the `us-central1` / `asia-south1` separation for latency and network cost. Any region migration requires coordinated client/backend changes and explicit approval.
- Do not treat successful builds or emulator tests as production readiness or a security certification.

## Wallet and progression controls (source only)

- New server-owned collections: `wallets/{uid}` with `transactions`, `dailyRewards/{uid}`, `dailyRewardClaims/{uid}/days/{UTC-date}`, `missions/{uid}/days/{UTC-date}/items/{missionId}`, `missionClaims`, `progression` with XP events, achievement state/history, and `walletRateLimits`. Existing catch-all deny rules deny direct client access and writes to these paths.
- Callable functions derive UID from Firebase Auth context, reject unauthenticated/unverified password accounts, whitelist request fields, validate request IDs, enforce UTC idempotency/rate limits, and calculate all coin/XP awards server-side. Wallet aggregate integrity is checked against the ledger totals; achievement unlock history is immutable.
- Emulator tests cover concurrent duplicate daily claims, missed-day streak reset, daily/missions/progression/ledger atomicity, pagination, rate limiting, and client denial of wallet/reward/mission/progression access. These tests are not production verification.
- App Check enforcement is controlled by `ARENA_ENFORCE_APP_CHECK=true`, defaults off, and has not been enabled. Android App Check providers and Firebase Console setup are still required before considering enforcement.
- Online multiplayer source now validates turn/version/action server-side, keeps the full draw pile/hands/challenge legality in server-only state, and writes verified completion rewards/history/profile stats transactionally. Private room joins check existing block records and apply room-code rate limits. The client cannot choose rewards or write match documents.
- Batch 4 adds server-validated Gamer ID friend requests, symmetric relationships, block/unblock and relationship cleanup, owner-scoped presence/preferences/tokens/notifications, invitation authorization/expiry, membership/block-checked direct and room messaging, message length/rate limits, private reports and claim-protected moderator review. Firestore rules deny client writes and constrain record reads to owners, members, or participants.
- Push payloads are data-only and do not contain message content. Android requests notification permission before registering its FCM token. Invalid tokens are removed by Functions; in-app notifications remain the fallback.
- Scheduled turn timeout processing now exists in source, using a per-turn/version audit key, transaction retries, and no-show abandonment. It is not deployed or active. It resets active player state and does not award abandoned-match rewards.
- Batch 5 competitive data is server-owned: ratings change only from verified match placements with per-match idempotency; season/tournament administration checks the trusted `admin` claim; leaderboard responses recheck public profile visibility and friend-block relationships. Firestore rules deny direct client access and writes to competitive records, standings, brackets, and achievement unlocks.
- Competitive Emulator coverage includes unauthorized admin rejection, duplicate verified completion, hidden-profile rank gaps, tournament capacity/withdrawal/match assignment/reward calculation, and season finalization. Rule Emulator tests verify clients cannot read or modify representative competitive collections. These are source/emulator checks only.
- Known security/operations gaps: App Check enforcement remains off; no production monitoring, load/abuse test, retention/deletion policy, or moderator claim-provisioning process is verified. Presence is heartbeat-based and can be stale during force-stop/network loss. Text filtering is basic, reports require trusted moderator claims, chat has no end-to-end encryption or reliable delivery receipts, and Android/FCM behavior has not been tested on physical devices.
- Coin spending and ad/SSV rewards are not implemented. Tournament prize policy and secure admin-claim provisioning require owner approval. Keep deployment blocked pending App Check/configuration, social-abuse policy, monitoring, production IAM, and owner approval.
