# ArenaCards Firestore security

## Current policy

[`firestore.rules`](./firestore.rules) is deny-by-default:

- `users/{uid}` and `privateAccounts/{uid}` are readable only by the authenticated owner; clients cannot write either document.
- `publicPlayers/{uid}` is readable by its owner, or by another reader only when visibility is `public` and the document has no fields outside the approved public projection. Clients cannot write it.
- `usernames/{normalized}` and `gamerIds/{gamerId}` are not client-readable or writable.
- Every unlisted path is denied.

The app's profile creation, username claim/change, Gamer ID allocation, profile projection updates, and trusted statistics are handled by callable Cloud Functions using Admin SDK credentials supplied by the Firebase runtime. No service-account key is embedded in the Android app or repository.

## Protected invariants

- Firebase Auth UID is the permanent account key.
- Username uniqueness is case-insensitive and enforced with a Firestore transaction against a server-only reservation.
- Gamer IDs are allocated with a server-generated random candidate checked transactionally against a server-only index; collisions are retried. Profile update code never accepts a Gamer ID.
- The profile update callable only accepts editable presentation fields and visibility. Account status, match records, XP, level, and any future roles/wallet balances remain trusted-backend controlled.
- Profile visibility defaults to private. Public player reads expose only the approved projection; private contact/authentication fields are never copied into it.
- Firebase Security Rules do not constrain Admin SDK access. Function authorization, input validation, dependency review, and deployment configuration therefore remain security-critical.

## Rule tests

Firestore Emulator tests are in [`firebase/tests/firestore.rules.test.mjs`](./firebase/tests/firestore.rules.test.mjs). They verify owner reads, cross-user private-read denial, public/private projection visibility, denial of client writes to protected profile/index documents, and refusal to expose a public document containing an unexpected private field.

Run them locally from `firebase`:

```powershell
npm.cmd install
npm.cmd run test:rules
```

These tests passed against the local Firestore Emulator in this workspace. They do **not** prove that rules or functions have been deployed, and do not verify production Firebase configuration or Admin SDK behavior. Cloud Functions source helper tests are separate (`cd firebase/functions; npm.cmd install; npm.cmd test`).

The local Firebase CLI/test dependency tree reports 13 moderate-severity npm audit findings. These findings are confined to development/emulator tooling; they are not Cloud Functions runtime dependencies. The Functions runtime dependencies were checked with `npm audit --omit=dev` and reported no known vulnerabilities. Review/upgrade the test CLI dependency tree before using it in CI or on a shared developer workstation.

## Before production

1. Review Firebase Console Firestore database location and billing plan; do not enable paid services without approval.
2. Deploy only the reviewed `firestore.rules` and callable functions after explicit approval. Do not use permissive development rules.
3. Configure and test Firebase App Check for Android; callable App Check enforcement is not enabled in this phase.
4. Confirm the Functions region matches the chosen Firestore location and review Cloud Functions deployment/build/invocation pricing.
5. Add integration tests for callable transactions in the Functions Emulator, including simultaneous same-name claims, ID collision retry, existing-profile idempotence, username release/change, unverified email rejection, and statistics/Gamer ID immutability.
6. Audit moderation rules for supported languages and abuse cases. The current profanity package is only a basic filter; it is not a substitute for policy, reporting, or human moderation.
7. Re-review public fields and rules before adding social search, blocking, reports, matchmaking, leaderboards, or private account data.

No production backend security verification has been performed. No services or rules were deployed from this work.
