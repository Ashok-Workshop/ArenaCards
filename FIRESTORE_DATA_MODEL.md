# ArenaCards Firestore data model

All document paths use Firebase Authentication UID as the account key. Emails, phone numbers, passwords, ID tokens, and other sign-in secrets are not copied into these documents. Firebase Authentication remains authoritative for account credentials and verified contacts.

## Collections

### `users/{uid}` — canonical, owner-readable profile

Created/updated by trusted Cloud Functions only:

| Field | Type | Authority / use |
|---|---|---|
| `gamerId` | string, `AR-` plus six digits | Assigned once by trusted function; never changed by profile update |
| `username` | string | User-selected value after trusted validation |
| `usernameNormalized` | string | Trimmed lowercase unique reservation key |
| `displayName` | string | User-editable, 1-30 characters |
| `avatarId` | string | One of the server allowlisted avatar IDs |
| `createdAt` | Firestore Timestamp | Server creation time |
| `updatedAt` | Firestore Timestamp | Server update time |
| `accountStatus` | string | Trusted backend controlled; currently `active` |
| `profileVisibility` | `public` or `private` | User-editable through callable function; defaults to `private` |
| `totalMatches`, `wins`, `losses`, `draws` | integer | Trusted game services only; initialized to zero |
| `experiencePoints` | integer | Trusted game services only; initialized to zero |
| `playerLevel` | integer | Backend-derived from XP; initialized to one |

Clients may read only their own canonical profile. All direct client writes are denied.

### `privateAccounts/{uid}` — private account metadata

Owner-readable, server-written. Currently contains UID, account status, and server timestamps only. Contact details remain in Firebase Authentication and are not mirrored. Future private preferences/account metadata can be added here after a privacy and rules review.

### `publicPlayers/{uid}` — approved public projection

Written from the canonical profile by trusted functions. Contains only Gamer ID, username and normalized username, display name, avatar ID, timestamps, visibility, match statistics, XP, and level. The public projection omits account status, roles, authentication details, email, phone, and tokens. A private profile is not readable by other clients. The rules also reject public reads if unexpected extra keys exist.

### `usernames/{usernameNormalized}` — unique username reservation

Server-only index containing the owning UID, display username, and reservation time. Direct client reads and writes are denied. A transaction checks and reserves this normalized document to enforce case-insensitive uniqueness under concurrency.

### `gamerIds/{gamerId}` — permanent Gamer ID reservation

Server-only index mapping each `AR-######` Gamer ID to its owning UID and allocation time. Direct client reads and writes are denied. The trusted creation transaction checks this index before allocating an ID.

### Wallet and progression collections — trusted-backend only

These collections are keyed by Firebase UID. Clients access them through authenticated callable Functions only; Firestore rules deny direct client reads and writes.

| Path | Purpose |
|---|---|
| `wallets/{uid}` | Coin balance, lifetime earned/spent totals, and timestamps. The invariant is `totalCoinsEarned - totalCoinsSpent == coinsBalance`. |
| `wallets/{uid}/transactions/{transactionId}` | Immutable committed coin ledger entries with amount, before/after balances, source, timestamp, and idempotency metadata. |
| `dailyRewards/{uid}` | Current UTC reward streak and most recent claim date. |
| `dailyRewardClaims/{uid}/days/{yyyy-MM-dd}` | Idempotency record for one daily claim per UTC date. |
| `missions/{uid}/days/{yyyy-MM-dd}/items/{missionId}` | Server-derived daily mission progress and claim state. |
| `missionClaims/{uid}/claims/{claimId}` | Idempotency record for one mission claim per day. |
| `progression/{uid}` | Trusted XP total and derived level, separate from coin balance. |
| `progression/{uid}/experienceEvents/{eventId}` | Immutable XP award history. |
| `progression/{uid}/achievements/{achievementId}` | Achievement progress and current unlock state. |
| `progression/{uid}/achievementHistory/{achievementId}` | Immutable first-unlock record. |
| `walletRateLimits/{uid}/operations/claims` | Server-side claim-operation rate-limit state. |

Daily awards use a seven-day UTC schedule, with the final award capped for longer streaks. Active missions are daily login and a three-day login streak; online-match mission progress is not yet integrated with completed matches. Verified match-end rewards are implemented separately by the multiplayer service. No coin-spending feature is currently implemented.

## Callable functions

- `checkUsernameAvailability`: authenticated feedback only; not a reservation and not a security guarantee.
- `createPlayerProfile`: requires an authenticated UID (and a verified email for password-provider sessions). In one Firestore transaction it creates the username and Gamer ID reservations, canonical profile, private metadata, and public projection. Repeated calls for an existing UID return that profile without reallocating its Gamer ID.
- `updatePlayerProfile`: changes only username, display name, avatar, visibility, and timestamp. Username release/claim is transactional. Gamer ID, account status, roles, XP, and match statistics are preserved.
- `searchPublicPlayers`: authenticated prefix lookup through the server-only username index, returning only profiles whose visibility is public and only approved lookup fields.
- Wallet callables: `getArenaAccount`, `claimDailyReward`, `claimMissionReward`, and `getWalletTransactions`. They derive identity from Firebase Auth, validate requests, and write rewards/ledger/progression atomically. Clients cannot submit coin or XP amounts.
- Multiplayer callables: `joinMatchmaking`, `cancelMatchmaking`, `createPrivateRoom`, `joinPrivateRoom`, `getPrivateRoom`, `setPrivateRoomReady`, `leavePrivateRoom`, `startPrivateRoom`, `observeOnlinePlayer`, `heartbeatOnlineMatch`, `reconnectToMatch`, `submitMatchAction`, `processExpiredTurn`, and `getMatchHistory`. They derive identity from Firebase Auth and enforce membership, request idempotency, game rules, expected versions, and backend time. Match result and reward fields are never accepted as client authority.

Firestore server timestamps are stored as `createdAt` and `updatedAt`. The callable response converts timestamps to millisecond values for Android model mapping. Firestore indexes/transactions protect reservations; the client-side availability check is advisory only.

## Online multiplayer

All competitive gameplay state is written by callable Functions using the explicitly configured `arenacards-f3279` / `unoashok` Firestore client. Clients may listen to approved read-only projections but cannot create or change matchmaking, room, match, wallet, result, or history documents directly.

| Path | Purpose / access |
|---|---|
| `onlinePlayers/{uid}` | Server-maintained queue/room/match status and heartbeat metadata; only the owner may read it. |
| `matchmakingQueue/{uid}` | Server-only queue entry for one authenticated player. |
| `matchmakingBuckets/{playerCount}` | Server transaction coordination for concurrent queues by player count. |
| `privateRooms/{roomId}` | Server-owned room membership, readiness, configurable standard rules, status, and expiry. Members may read; direct client writes are denied. |
| `roomCodeLookups/{10-character-code}` | Server-only room-code index. Guessing attempts are rate limited. |
| `roomJoinRateLimits/{uid}` | Server-only room-code attempt window. |
| `onlineMatches/{matchId}` | Public match projection: player seats/card counts, current turn, top/discard history, public color, deadline, and verified result. Participants may read; client writes are denied. |
| `onlineMatches/{matchId}/hands/{uid}` | A participant's private hand/version. Rules allow only that UID, and only while they are a match participant, to read it. |
| `onlineMatches/{matchId}/private/state` | Server-only authoritative draw pile, all hands, challenge legality, and game state. Clients cannot read or write it. |
| `onlineMatches/{matchId}/actions/{uid_requestId}` | Server-only idempotency records for submitted actions. |
| `onlineMatches/{matchId}/timeouts/{requestId}` | Server-only client-request and scheduled-timeout audit/idempotency records. |
| `onlineMatchHistory/{uid}/matches/{matchId}` | Per-player verified match history; only the owner may read. |
| `multiplayerRequests/{uid}/{requestId}` | Server-only request idempotency record, including private-room creation. |
| `userBlocks/{uid}/users/{otherUid}` | Server-read block records used by room joins, invitations, and chat; the owner may read and manage blocks only through callable Functions. |

Matchmaking has no CPU seats. Private-room rules support 30/45/60 seconds per turn, Wild Draw Four challenge on/off, standard mode, and no stacking; changes are restricted to waiting rooms. Match reward constants are 25 coins and 50 XP per verified participant, plus 75 coins and 50 XP for the winner. Rewards/history/profile statistics are committed transactionally only for a verified completed match with a winner; abandoned/stalemate matches do not award winner rewards.

The Android client listens to the public match and its own hand, accepting a combined state only when versions agree. Actions include the expected turn and state version and do not optimistically mutate the hand/table. A scheduled timeout worker now processes due matches in source with a per-turn/version idempotency record, in addition to the client-requested timeout path. The scheduled job is not deployed or active.

## Social, chat, moderation, and notification data

| Path | Purpose / access |
|---|---|
| `friendships/{uid}/friends/{friendUid}` | Symmetric server-written friend summaries. The owner may read; create/remove goes through callables. |
| `friendRequests/{uid}/incoming/{requesterUid}` and `outgoing/{recipientUid}` | Mirrored pending request records. Each account can read its own direction; decisions are callable-only. |
| `userBlocks/{uid}/users/{otherUid}` | Owner-readable block list; block/unblock and relationship cleanup are server transactions. |
| `playerPresence/{uid}` | Server-written foreground heartbeat and expiry. Not client-readable or writable. Presence may appear stale after force-stop or network loss. |
| `roomInvitations/{uid}/inbox/{invitationId}` and `outbox/{invitationId}` | Recipient/sender-scoped invitation copies with room membership checked transactionally on acceptance. |
| `conversations/{conversationId}` and `/messages/{messageId}` | Friend-only direct conversation metadata and messages. Reads require membership; all writes use callable Functions. Messages are paginated and rate-limited. |
| `privateRooms/{roomId}/messages/{messageId}` | Member-only room chat. Writes use callable Functions and are denied to clients. |
| `reports/{reportId}` | Reporter-readable report records with validated optional message/match references; moderator review requires a trusted Auth claim. |
| `inAppNotifications/{uid}/items/{notificationId}` | Owner-readable in-app activity records with page cursors. |
| `notificationPreferences/{uid}` | Owner-readable preferences; changes are callable-only. |
| `notificationTokens/{uid}/devices/{deviceId}` | Server-only device token records; tokens are never client-readable. FCM payloads omit message body. |
| `moderationAudit/{auditId}`, `messageRateLimits/{uid}`, `reportRateLimits/{uid}` | Server-only moderation audit and abuse-control state. |

Composite query indexes are declared in [`firebase/firestore.indexes.json`](./firebase/firestore.indexes.json). Neither indexes, rules, nor Functions have been deployed.

## Competitive profiles, seasons, and tournaments

Competitive state is written only by trusted Functions and is never accepted from client-submitted match results. Global rating and season statistics change only after a verified multiplayer completion; the completion marker makes processing idempotent.

| Path | Purpose / access |
|---|---|
| `competitiveProfiles/{uid}` | Server-owned all-time Elo rating, verified match record, win rate eligibility, streaks, and public-ranking eligibility synchronized from profile visibility. Direct client access is denied. |
| `competitiveMatchRecords/{matchId}` | Server-owned verified result marker and placement/rating deltas; prevents duplicate competitive processing. |
| `competitiveRatingHistory/{uid}/matches/{matchId}` | Private server-owned rating changes and placement history. |
| `competitiveSeasonState/current` | Server-owned active-season pointer. |
| `competitiveSeasons/{seasonId}` | Owner-admin-created schedule, rewards, and lifecycle status. `players/{uid}` holds that player's server-calculated seasonal record; `finalResults/{uid}` stores final placement. |
| `seasonLeaderboardEntries/{seasonId_uid}` | Server-maintained public-ranking projection for the season; public identity is still rechecked against `publicPlayers` before display. |
| `competitiveSeasonFinalizations/{seasonId}` | Idempotency marker for scheduled/manual season close and rewards. |
| `tournaments/{tournamentId}` | Server-owned configuration, capacity/registration list, status, bracket, and champion. Bracket rounds are a map of round arrays because Firestore disallows nested arrays. |
| `tournamentParticipants/{uid}/entries/{tournamentId}` | Player-scoped registration and completion/place history. |
| `competitiveAchievements/{uid}/unlocks/{achievementId}` | Server-unlocked competitive achievements. In-app unlock notifications use the existing `inAppNotifications` path. |

Competitive callables provide global/friend leaderboards, the caller's profile, season history/current state, tournament listing/details/registration/withdrawal, and owner-claim-protected season/tournament administration. `finalizeCompetitiveSeasons` runs hourly in UTC in source; it is not deployed. Season and tournament rewards use the existing server-owned wallet and XP ledgers. Tournament configuration currently supports the champion reward only.

## Future compatibility

Friend requests, blocks, room invitations, chat, reports, notifications, leaderboards, seasons, tournaments, and competitive achievements now have source schemas and callable authorization. Full administrative tooling remains future work; it must use trusted authorization and must not accept client-supplied match results or statistics.

Wallet and progression are separate from the public profile schema; only trusted backend functions may update the existing profile XP/level projections. Social data remains separate from public profile projections. Account deletion, retention, and moderator-claim provisioning require owner policy and operational setup.
