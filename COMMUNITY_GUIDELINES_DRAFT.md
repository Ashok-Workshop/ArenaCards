# ArenaCards community guidelines and moderation operations — owner review draft

Updated: 2026-11-16

**Draft for owner review. Not legal terms, a staffed moderation service, or proof that production moderation is active.** Configure the actual support channel, moderators, response process and retention schedule before enabling public user-generated content.

## Community expectations

ArenaCards is a competitive and social card-game environment. Players are expected to:

- Treat other players respectfully. Do not harass, threaten, bully, stalk, or target people based on personal characteristics.
- Do not post hate speech, sexual exploitation, threats of violence, encouragement of self-harm, or illegal content.
- Do not spam, flood chat, impersonate another player or staff member, manipulate usernames/Gamer IDs, or use deceptive links.
- Do not share another person's private information, account credentials, phone number, address, screenshots of private conversations, or identifying information without permission.
- Do not exploit game bugs, manipulate matchmaking or rankings, abuse reward claims, trade/sell accounts, or attempt to interfere with the service.
- Keep room names, usernames, display names and messages suitable for the app's approved age rating.

## In-app safety controls

The current source includes player blocking, friend-request controls, player/message reporting, basic message filtering, server-side message rate limits and moderator report-review callables. These features are source-level implementations and are not live until the backend is deployed and verified.

Blocking should prevent further direct social interactions between the two accounts, including friend requests, room invitations and direct chat. Report controls should capture only the evidence needed for review and must not expose reporter identity to the reported player.

The current profanity filter is a limited automated signal. It cannot reliably detect context, coded abuse, threats, grooming, scams, or all harmful content. It is not a substitute for human moderation.

## Proposed report workflow

1. A player submits a report from the relevant player/message surface and chooses an available reason.
2. The server validates authentication, target, reason, rate limit and idempotency; the client cannot write moderation records directly.
3. The report enters a restricted moderator queue. Reporters can see their own submission status, not internal moderator notes.
4. An authorized reviewer checks relevant context and chooses a documented outcome: no action, warning, content removal, temporary restriction, or account suspension, where those actions are implemented and approved.
5. The reviewer records a reason and action in a restricted audit trail. High-impact actions should require a second reviewer or owner escalation.
6. The reporter receives a minimal status update without revealing confidential details about another account.
7. Appeals and emergency escalation are handled through the owner-approved support channel.

## Moderator access and operations

- Provision moderator/admin custom claims only through an owner-controlled, authenticated process. Never expose claim assignment to clients.
- Use least-privilege roles: moderators should review and take only the actions explicitly granted to them; administrators manage role and policy configuration.
- Require MFA on privileged owner accounts where supported, periodically review access, and promptly revoke departed moderators.
- Log reviewer identity, report ID, action, timestamp and reason. Do not log passwords, auth tokens, message bodies or unnecessary personal data.
- Establish staffed coverage, escalation ownership, response targets, evidence access controls, appeals and emergency response before opening public chat.
- Restrict report and moderation audit access. Do not promise confidentiality or response times until the owner can operationally meet them.

## Data and retention decisions required

The owner must approve retention periods and deletion exceptions for report evidence, moderation audit, chat content, player identifiers, sanctions and appeals. See `ACCOUNT_DATA_LIFECYCLE.md`. Do not silently delete evidence needed for a live safety investigation or retain it indefinitely without an approved basis.

## Product safeguards to verify before launch

- Confirm block/report affordances are visible and accessible from player profiles, direct chat, room chat and other relevant surfaces.
- Confirm blocked players cannot bypass controls through alternate entry points, invitations or search results.
- Test abusive content, spam, self-report attempts, duplicate reports, rate-limit boundaries, moderator authorization and audit creation in emulator tests.
- Test moderator actions against a staging environment with dedicated accounts before production deployment.
- Provide a monitored contact for urgent safety reports and account appeals.
- Review age rating, target audience, child-safety obligations and user-generated-content policy with a qualified reviewer.

## Publication gate

Do not present this draft as an enforceable policy until the owner has approved it, configured an actual moderation team and support contact, verified the shipped controls, and reviewed current platform and jurisdiction requirements.