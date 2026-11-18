# ArenaCards account data lifecycle and deletion design

Updated: 2026-11-16

## Status and scope

This is an engineering inventory and launch-readiness design, not a published privacy policy, legal opinion, or claim that deletion is already available. The current repository contains no complete account-deletion callable or verified end-to-end deletion workflow. Do not advertise account deletion as available until the implementation and owner verification steps below are complete.

No Firebase deployment, billing change, production-data access, or production mutation was performed while preparing this document.

## Data categories visible in current source

| Category | Source collections / records | Notes |
|---|---|---|
| Account identity and profile | Firebase Authentication UID; users/{uid}; privateAccounts/{uid}; publicPlayers/{uid}; usernames/{normalizedUsername}; gamerIds/{gamerId} | Username, display name, avatar, Gamer ID, profile visibility and profile statistics. |
| Wallet and progression | wallets/{uid} and transactions; dailyRewards/{uid}; dailyRewardClaims; missions/{uid}/days/{day}/items; missionClaims; progression/{uid} and achievement / experience-event subcollections; walletRateLimits | Balance, earned/spent totals, ledger entries, reward claims, streaks, XP and achievement history. |
| Competitive records | competitiveProfiles/{uid}; competitiveRatingHistory/{uid}/matches; competitiveAchievements/{uid}/unlocks; competitiveSeasons/{seasonId}/players/{uid}; seasonLeaderboardEntries; competitiveMatchRecords; tournamentParticipants/{uid}/entries; tournament bracket / final-result records | Some records are embedded in season/tournament documents and may include player identity or match outcomes. Deletion must not corrupt other players' records or active brackets. |
| Multiplayer and match history | onlinePlayers/{uid}; matchmakingQueue/{uid}; onlineMatches/{matchId} with private state, hands, actions and timeouts; onlineMatchHistory/{uid}/matches/{matchId}; multiplayerRequests/{uid}/operations; room code lookup and rate-limit records | Match documents are shared by participants. A player's personal deletion must not erase another participant's valid match history. |
| Social graph and safety | friendships/{uid}/friends; friendRequests/{uid}/incoming and /outgoing; userBlocks/{uid}/users; roomInvitations/{uid}/inbox and /outbox; reports; moderationAudit | Reports and moderation/audit evidence may need restricted retention for safety, abuse handling, dispute resolution, or legal obligations. The retention period has not been approved. |
| Chat and notifications | conversations, messages, privateRooms; inAppNotifications/{uid}/items; notificationPreferences/{uid}; notificationTokens/{uid}/devices; playerPresence; messageRateLimits; reportRateLimits | Message content is stored server-side; push payloads are data-only and do not include message bodies. No end-to-end encryption is implemented. |
| Device-local data | Android DataStore settings, onboarding state, practice history and saved offline session | Local to the app installation; not automatically erased by a server-side account deletion. Android controls uninstall / clear-data behavior. |

This inventory is derived from current Cloud Functions source and should be rechecked whenever new collections or external SDKs are added.

## Deletion behavior required before launch

1. Provide an authenticated, clearly labeled in-app account deletion entry point and a web-accessible deletion request path if required by the distribution platform's current policy.
2. Require recent authentication / reauthentication before irreversible deletion. Explain consequences before final confirmation.
3. Create a server-side deletion request with an idempotency key and durable status (REQUESTED, PROCESSING, COMPLETED, FAILED_RETRYABLE, NEEDS_REVIEW). Never trust a client-supplied UID; derive it from verified auth context.
4. Prevent new matchmaking, room invitations, friend requests, chat sends, reward claims and profile edits once deletion is requested. Handle active matches deliberately (finish, forfeit, or wait for a bounded timeout) without changing another player's state unfairly.
5. Remove or anonymize profile identity and release username / Gamer ID reservations only after checking references and race conditions.
6. Delete or anonymize user-owned wallet, progression, notification, presence, queue, friend, block, invite, chat and device-token data according to the approved policy. Revoke FCM tokens and Firebase Authentication sessions.
7. Preserve only the minimum records the owner/legal reviewer has explicitly approved for fraud prevention, safety reports, moderation audit, financial ledger integrity, tournament integrity or legal obligations. Retained records must be access-restricted and de-identified where feasible.
8. For shared match, tournament, conversation and report records, replace the deleted player's public identity with a stable non-identifying tombstone and retain only the minimum fields needed for other participants and integrity. Never recursively delete a shared parent document as if it belonged to one user.
9. Use bounded, retryable background batches for recursive subcollection deletion. Firestore parent-document deletion does not automatically delete subcollections. Record progress, retry failures, and verify completion before deleting the Authentication user.
10. Return a clear completion status to the user. Define backup expiry and explain that deleted data may persist in backups until the documented backup lifecycle expires, if backups are enabled.

## Decisions required from the owner before implementing the final deletion policy

- Which jurisdiction(s) and age groups will the app serve, and who is the responsible legal entity / privacy contact?
- Which match-history, wallet-ledger, report, moderation-audit, tournament and fraud-prevention records must be retained, and for how long?
- Should account deletion be immediate or include a disclosed cooling-off period? What happens to an active online match?
- What is the approved treatment of earned but unspent virtual coins, tournament rewards and pending claims on deletion?
- What is the approved privacy/support email and public deletion-request URL?
- What is the configured backup retention and recovery policy for the actual Firebase project?

Do not invent these values or publish a policy until the owner has approved them.

## Related security requirements

- Firestore rules must continue to deny client writes to identity reservations, wallet/ledger, competitive results, shared match state, reports, moderation audit and notification tokens.
- App Check must be configured and tested in Firebase Console before enforcement is enabled for production callables.
- Admin/moderator claims must be provisioned through a controlled owner process; clients must never grant themselves roles.
- Deletion operations must be audited without logging secrets, message bodies, phone numbers, email addresses or unnecessary personal data.
- Add emulator tests for ownership, reauthentication, idempotency, partial-batch retries, shared-match anonymization, active-match handling, retained-record access controls and final Auth-user removal.

## Launch gate

Account deletion is not complete until the callable/workflow, client UI, web request path where applicable, data retention schedule, emulator tests, production IAM/App Check review, and owner verification have all been completed. This document is a prerequisite inventory, not a substitute for those tasks.