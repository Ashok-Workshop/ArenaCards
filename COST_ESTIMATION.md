# ArenaCards cost considerations

Updated: 2026-11-14

## Current known state

- Firebase project: `arenacards-f3279`
- Target Firestore database: `unoashok`, owner-reported as Enterprise Firestore Native in `asia-south1`.
- Scheduled backups are reported disabled by the owner; this work did not change backup settings.
- Callable Functions are configured for `us-central1`.
- Billing was not enabled or changed, and no production services or data were accessed by this work.
- The new settings and practice-history features use local Android DataStore and generated Android tones; they add no Firebase read/write or third-party audio-service traffic.

## Cost drivers to review

No monetary estimate is provided because actual workload, regional pricing, discounts, retention, storage, and billing configuration have not been verified. Potential charges depend on Firestore document reads/writes/deletes, stored data and indexes, network transfer, Cloud Functions invocations/runtime, logging/monitoring, backups if enabled, and any Enterprise-specific pricing terms.

The `us-central1` Functions / `asia-south1` Firestore separation may add network latency and cross-region transfer costs. This code change deliberately preserves the deployed-source region rather than silently migrating callable endpoints. Evaluate a coordinated region change against latency, transfer costs, deployment risk, and old-client compatibility.

Before any production deployment, the owner should review the current Firebase/Google Cloud price sheets and project billing account, usage quotas, budget alerts, retention/backups, and expected concurrent traffic. No billing change is authorized by this document.

## Wallet and rewards cost considerations

- Wallet snapshot calls read the wallet, daily reward, progression/profile, current mission and achievement state. Claim calls perform multiple transactional reads/writes to wallet, ledger, claim, mission/progression, and profile projection documents. Each ledger entry and XP event adds stored data; transaction-history pagination reads only a bounded page (1–50 entries).
- Daily-login and streak rewards add Firestore writes for claims and progression. Duplicate/concurrent claims may retry transactions and therefore increase reads. Rate-limit documents add a small transactional read/write per successful claim.
- No estimate is claimed: actual cost depends on account count, claim frequency, transaction retries, ledger retention, index/storage growth, Functions execution, and cross-region transfer between `us-central1` and `asia-south1`.
- No production wallet traffic, Functions deployment, AdMob integration, paid service, or billing change has occurred. Review budgets, quotas, data retention, and backup requirements before enabling the feature in production.

## Multiplayer cost and scaling considerations

- Each matchmaking/room/action/heartbeat/timeout/history request invokes a callable Function. Match listeners read the public match plus each client's own hand; writes update versioned match/private-hand state and player status. These snapshots and per-turn transactions grow with active player count and turn frequency.
- Reconnect heartbeats are sent every 25 seconds while a match is open. This creates recurring callable invocations and Firestore reads/writes; expected concurrent matches and match duration must be load-tested before production rollout.
- Private room codes add server-side lookup and rate-limit reads/writes. Match history and idempotency/timeout/action records add stored documents; retention/cleanup policy is not yet automated.
- The functions region remains `us-central1` while Firestore is in owner-reported `asia-south1`. Cross-region latency/transfer is a possible cost and UX factor; no measured latency or price estimate is claimed.
- Clients can still request timeout processing after the deadline, and a scheduled backend worker is now also present in source. The scheduled job is not deployed; its operational cost and production behavior remain unverified.

## Social, chat, notification, and scheduled-processing cost drivers (Batch 4)

- Friend/request/block/invitation/report/notification list calls read a bounded page plus related profile or presence records; pagination keeps each page capped at 30 items. Presence heartbeats are sent about once per foreground minute and add callable invocations and writes.
- Direct/room chat writes a message and rate-limit state, updates conversation/room metadata, and creates in-app notifications. Realtime listeners generate document reads as messages arrive and conversations update. Message/report/moderation retention and deletion are owner policy decisions and must be budgeted.
- FCM delivery, token lifecycle, invalid-token cleanup, and retry/log behavior add service calls and operational costs. Push payloads contain no message body; in-app notifications remain stored and read through paginated queries.
- The scheduled timeout processor is configured for one-minute intervals; invitation expiry is also scheduled. Invocation/runtime costs depend on idle matches/invitations and due work. Cloud Scheduler and related deployment prerequisites/billing have not been verified. This source is not deployed.
- No price estimate is claimed. Measure query/read/write counts, listener churn, concurrent chat/matches, job runtime, retries, retention, and cross-region traffic in an approved staging environment before rollout.

## Competitive leaderboard, season, and tournament cost drivers (Batch 5)

- Leaderboard requests read a bounded competitive page plus public profile projections. Rank computation issues aggregate count queries; friend rankings additionally read up to 100 friend records, bidirectional block records, competitive profiles, and public projections. Query size and request frequency should be measured under expected usage.
- Verified match completion adds transactional competitive profile/history/result writes, optional season standings, achievement unlock/notification writes, and tournament bracket/participant notifications. Tournament brackets are embedded in one document and registration updates that document transactionally; contention is bounded by the 64-player source limit but should be load-tested before production.
- Season close is scheduled hourly in source. It reads a bounded top-50 standings page and may write final results, notifications, and wallet/XP ledger events for configured prize places. Scheduler invocation/runtime, transaction retries, index storage, and retention contribute to cost; this job is not deployed.
- There is no entry fee. Tournament rewards are configured server-side and currently support champion prizes only; production prize amounts/policy are not approved.
- No price estimate is claimed. Measure aggregate-query pricing, reads per leaderboard page, transaction retries, scheduled runtime, index growth, match/tournament concurrency, and cross-region traffic in an approved staging environment before rollout.
