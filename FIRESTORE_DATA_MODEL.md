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

## Callable functions

- `checkUsernameAvailability`: authenticated feedback only; not a reservation and not a security guarantee.
- `createPlayerProfile`: requires an authenticated UID (and a verified email for password-provider sessions). In one Firestore transaction it creates the username and Gamer ID reservations, canonical profile, private metadata, and public projection. Repeated calls for an existing UID return that profile without reallocating its Gamer ID.
- `updatePlayerProfile`: changes only username, display name, avatar, visibility, and timestamp. Username release/claim is transactional. Gamer ID, account status, roles, XP, and match statistics are preserved.
- `searchPublicPlayers`: authenticated prefix lookup through the server-only username index, returning only profiles whose visibility is public and only approved lookup fields.

Firestore server timestamps are stored as `createdAt` and `updatedAt`. The callable response converts timestamps to millisecond values for Android model mapping. Firestore indexes/transactions protect reservations; the client-side availability check is advisory only.

## Future compatibility

The UID-keyed profile shape can be referenced by future friend requests, blocks, reports, private rooms, matchmaking, and leaderboards. Those systems are not implemented. They must use trusted authorization and must not accept client-supplied match results or statistics.

No wallet or coin fields exist in this schema.
