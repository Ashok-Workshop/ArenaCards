# ArenaCards privacy notice — owner review draft

**Draft only — not approved for publication.** This text is a starting point for the app owner and a qualified privacy/legal reviewer. It is not legal advice and must not be published until the placeholders and data practices are verified.

**Service operator:** [LEGAL ENTITY / RESPONSIBLE PERSON]
**Privacy contact:** [APPROVED PRIVACY EMAIL]
**Effective date:** [DATE]
**Countries served:** [COUNTRIES / REGIONS]
**Age eligibility:** [APPROVED MINIMUM AGE AND AGE-ASSURANCE APPROACH]

## Information the service may process

Based on the current source code, ArenaCards may process:

- Account and authentication information managed by Firebase Authentication, including a Firebase user identifier and the sign-in provider. The exact identity fields depend on the provider used.
- Player profile details such as username, display name, avatar, Gamer ID, profile visibility and gameplay statistics.
- Wallet and progression records, including virtual coin balances, reward claims, transaction history, experience points and achievements.
- Online game information such as match state, player seats, actions, results, ratings, match history, private-room membership and tournament participation.
- Social and safety information such as friend requests, blocks, room invitations, chat messages, reports and moderation records.
- Notification information such as device push tokens, notification preferences and in-app notification records.
- Device-local settings, onboarding state, offline practice history and saved practice sessions.

The source includes online chat storage. End-to-end encryption is not implemented. Do not describe chat as end-to-end encrypted.

## How information is used

The current product design uses this information to authenticate accounts, maintain player profiles, run online matches, prevent duplicate or invalid reward claims, maintain wallet and competitive records, support friends and chat, deliver notifications, and investigate reports or abuse.

Offline practice history and preferences are stored locally by the Android app. Offline matches are not intended to award competitive currency or online rankings.

## Sharing and service providers

The current implementation is built around Firebase Authentication, Cloud Firestore, Cloud Functions and Firebase Cloud Messaging. The actual deployed services, processing locations, subprocessors and contractual terms must be confirmed by the owner before publication. Do not state that no data is shared with service providers.

## Retention and deletion

Specific retention periods have not been approved or verified. The repository currently lacks a complete, verified account-deletion workflow. Do not promise immediate or complete deletion until the account data lifecycle plan has been implemented and tested.

Before launch, publish the approved process for account deletion and privacy requests, including treatment of shared match records, safety reports, moderation audit records, wallet ledgers, chat history, device tokens and backups. The owner must provide a working privacy contact and, where required, a web deletion-request URL.

## Security

The source uses server-side functions and restrictive Firestore rules for sensitive operations. Automated emulator tests do not prove that production services are deployed or configured securely. Production App Check, access controls, monitoring, backups, retention and incident response must be verified before launch.

## Children and age eligibility

[OWNER / LEGAL REVIEW REQUIRED: define target age, minimum age, parental consent requirements where applicable, and whether the app is directed to children. Do not publish an age claim until this is decided.]

## Your choices and requests

[OWNER REVIEW REQUIRED: provide the actual account/profile controls, privacy request channel, applicable rights by jurisdiction, response process and escalation contact.]

## Changes to this notice

State how material changes will be communicated and record the date of the latest approved version.

---

## Owner publication checklist

- Replace every bracketed placeholder with verified information.
- Confirm actual Firebase project configuration, regions, enabled products, SDK telemetry and any analytics / advertising SDKs.
- Decide and implement account deletion and retention before claiming either is available.
- Review chat, user-generated content, reporting, moderation and safety obligations.
- Confirm age eligibility and jurisdiction-specific obligations with a qualified reviewer.
- Add a working in-app privacy link and any required public deletion-request page.
- Ensure the published notice matches the actual app release and third-party SDK inventory.