# ArenaCards Firestore security

## Current policy

[`firestore.rules`](./firestore.rules) is deny-by-default:

- `users/{uid}` and `privateAccounts/{uid}` are readable only by the authenticated owner; clients cannot write either document.
- `publicPlayers/{uid}` is readable by its owner, or by another reader only when visibility is `public` and the document has no fields outside the approved public projection. Clients cannot write it.
- `usernames/{normalized}` and `gamerIds/{gamerId}` are not client-readable or writable.
- Wallet, ledger, reward-claim, mission, progression/achievement, and wallet-rate-limit paths are unlisted and therefore denied to clients. Android reads and changes wallet state only through callable Functions.
- Online-match, private-room, online-player, and match-history writes are server-only. Match reads require authenticated membership, hand reads require both ownership and membership, room reads require membership, and status/history reads are owner-only.
- Social writes are server-only. Friendships, friend requests, blocks, room invitations, notification preferences/tokens, and in-app notification reads are scoped to their owner; conversations/messages require conversation membership; private-room chat requires room membership; reports are readable only by the reporter. Presence, rate limits, moderation audit, and token records are not client-readable.
- Every unlisted path is denied.

The app's profile creation, username claim/change, Gamer ID allocation, profile projection updates, and trusted statistics are handled by callable Cloud Functions. Functions use the official `@google-cloud/firestore` client with project `arenacards-f3279` and database `unoashok`; Cloud Functions Application Default Credentials are used at runtime. No service-account key is embedded in the Android app or repository.

The Android repository uses the supported named-database Firestore API and rejects a Firebase app configured for another project. Its callable Functions client remains explicitly in `us-central1`. `firebase.json` associates the restrictive rules with `unoashok`; no production rules or services have been deployed by this change.

## Protected invariants

- Firebase Auth UID is the permanent account key.
- Username uniqueness is case-insensitive and enforced with a Firestore transaction against a server-only reservation.
- Gamer IDs are allocated with a server-generated random candidate checked transactionally against a server-only index; collisions are retried. Profile update code never accepts a Gamer ID.
- The profile update callable only accepts editable presentation fields and visibility. Account status, match records, XP, level, and any future roles/wallet balances remain trusted-backend controlled.
- Profile visibility defaults to private. Public player reads expose only the approved projection; private contact/authentication fields are never copied into it.
- Coin balances are server-owned and reconciled with `totalCoinsEarned - totalCoinsSpent`. Daily claims use UTC-date idempotency; mission claims, XP events, achievements, and immutable unlock history are written by trusted Functions transactions. The client cannot choose reward amounts or report unverified match results.
- Online match actions validate the authenticated participant, expected turn/version, and legal server transition. The draw pile, full hands, and Wild Draw Four legality are kept in a server-only state document. Verified winner rewards, match history, profile statistics, wallet ledger entries, and XP events are written transactionally and idempotently.
- Private room-code attempts are rate limited. Room joins, invitations, and chat check authorization, friendship/room membership, and blocks in both directions. Block/unblock, reporting, and notification settings are exposed through authenticated callable Functions rather than client writes.
- Chat message length and send rates are enforced in trusted Functions. FCM payloads omit message text. Report review requires a trusted `moderator` or `admin` custom claim; provisioning and monitoring these claims is an owner action.
- Online timeout transitions and invitation expiry have scheduled Functions source, with transaction-based idempotency. They remain inactive until owner-approved deployment and operational configuration.
- Competitive ratings, season standings/rewards, tournament registrations/brackets/rewards, and achievement unlocks are backend-only. Verified match completion is the sole rating input and an immutable per-match record blocks replay. Admin season/tournament lifecycle callables require the trusted `admin` custom claim; clients cannot create production seasons/tournaments or alter ratings/brackets.
- Firestore Security Rules do not constrain trusted server-client access. Function authorization, input validation, dependency review, and deployment configuration therefore remain security-critical.

## Rule tests

Firestore Emulator tests are in [`firebase/tests/firestore.rules.test.mjs`](./firebase/tests/firestore.rules.test.mjs). They verify owner reads, cross-user private-read denial, public/private projection visibility, denial of client writes to protected profile/index, wallet/reward/mission/progression, competitive profile/result/season/tournament/achievement paths, participant/own-hand access, social owner/member/report privacy, room/history access, and refusal to expose hidden match state or accept client writes. Functions Emulator tests additionally cover competitive rating idempotency, hidden-profile rank gaps, tournament registration/match assignment/rewards, season finalization, social authorization, blocks, invitations/expiry, chat/reporting/notifications, timeout-worker races, matchmaking, stale actions, and verified history.

Run the named-database rules tests locally from `firebase`:

```powershell
npm.cmd install
npm.cmd run test:rules
```

These emulator tests exercise the `unoashok` database and do **not** prove that rules or functions have been deployed or verify production Firebase configuration. Cloud Functions helper tests are separate (`cd firebase/functions; npm.cmd test`). Callable profile transaction tests run against the named Firestore Emulator with `cd firebase; npm.cmd run test:functions:emulator`.

Previously recorded npm audit counts predate the current dependency manifests. Re-run `npm.cmd audit` from `firebase` and `npm.cmd audit --omit=dev` from `firebase/functions` before deployment; no current audit result is claimed here.

## Before production

1. Review Firebase Console Firestore database location and billing plan; do not enable paid services without approval.
2. Deploy only the reviewed `firestore.rules` and callable functions after explicit approval. Do not use permissive development rules.
3. Configure and test Firebase App Check for Android; callable App Check enforcement is not enabled in this phase.
4. Review the current `us-central1` Functions / `asia-south1` Firestore region separation, latency, egress, and deployment migration risks before considering a region change.
5. Add load/race tests for multiplayer queue contention and end-to-end testing against approved production-like configuration. Existing emulator coverage is not production performance verification.
6. Audit moderation rules for supported languages and abuse cases. The current profanity package is only a basic filter; it is not a substitute for policy, reporting, or human moderation.
7. Re-review public fields and rules when extending friend, invitation, block, report, chat, leaderboard, season, or tournament features.

No production backend security verification has been performed. No services or rules were deployed from this work.
