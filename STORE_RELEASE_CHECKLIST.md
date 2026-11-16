# ArenaCards Google Play and release-readiness checklist

Updated: 2026-11-16

**Status: preparation checklist only.** The owner handles physical-device testing and APK generation. This document tracks the other release gates; it does not mean the app is approved for release.

## App identity and build

- [ ] Confirm application ID `com.arenacards.game`, app name, version name and monotonically increasing version code.
- [ ] Confirm the final launcher icon, adaptive icon, monochrome icon and branded screenshots are original and legible at required sizes.
- [ ] Confirm release build uses the correct Firebase Android app and environment; do not ship the temporary CI placeholder configuration.
- [ ] Configure a private release signing key and securely back it up. Never commit the keystore, passwords or signing properties.
- [ ] Validate release minification / R8 rules and inspect mapping files; verify Firebase, Compose, Credential Manager and notification paths in a minified build.
- [ ] Confirm no debug endpoints, emulator-only configuration, test credentials, verbose sensitive logs or developer menus are present.
- [ ] Verify crash reporting / analytics SDK inventory and consent requirements before enabling any such SDK.

## Account, privacy and data safety

- [ ] Publish an owner-approved privacy notice that accurately names the responsible entity, contact, data categories, purposes, processors, regions, retention and user rights.
- [ ] Implement and test the in-app account deletion path and public deletion-request path where required by current Play policy. See `ACCOUNT_DATA_LIFECYCLE.md`.
- [ ] Complete Google Play Data safety answers from the final release SDK and backend inventory; do not infer answers from this checklist.
- [ ] Confirm Firebase Authentication providers, email verification, password reset, Google OAuth fingerprints, phone/SMS region policy and reauthentication behavior.
- [ ] Verify App Check provider registration and production enforcement with a staged rollout plan.
- [ ] Approve retention schedules and implement cleanup for chat, notifications, device tokens, reports, moderation audit, match records and backups.
- [ ] Verify account deletion does not erase shared match/tournament integrity records or retain unnecessary personal data.

## User-generated content, social features and safety

- [ ] Publish community standards and terms for usernames, display names, chat, friend requests, room invitations and player-generated content.
- [ ] Provide in-app reporting and blocking controls that are easy to find; verify block behavior across friend requests, invitations, direct messages, room chat and leaderboards.
- [ ] Provide a staffed, monitored support/moderation contact and a documented process for reviewing, escalating and resolving reports.
- [ ] Define response targets, appeals, repeat-offender handling, evidence access, moderator audit, and emergency escalation.
- [ ] Verify that moderation roles are provisioned securely by the owner and that clients cannot grant themselves roles.
- [ ] Do not describe the profanity filter as comprehensive moderation. Confirm human review and abuse response operations exist before enabling public chat.
- [ ] Confirm privacy settings and public-player discovery match the published notice and store declarations.

## Ads, payments and virtual currency

- [ ] If AdMob is used, create and verify the owner's ad account, register the app, configure production ad-unit IDs, and keep test IDs in development builds.
- [ ] Implement rewarded-ad server-side verification and idempotent reward crediting before promising rewards for watching ads.
- [ ] Add required ad disclosures and ensure ad placement does not mislead or interfere with gameplay.
- [ ] Review consent, age treatment, personalized-ad settings and any applicable privacy requirements before ad requests.
- [ ] No real-money purchase or coin-spending catalog is currently approved. Do not add purchase buttons, pricing, or real-money flows until product rules and Play Billing obligations are agreed.
- [ ] If virtual items or purchases are introduced, define refund, chargeback, account deletion and lost-item support rules and verify the current Play billing policy.

## Gameplay, online services and reliability

- [ ] Owner completes physical-device QA for offline gameplay, UNO catch timer, Wild Draw Four challenge, pause/resume, orientation, process death and accessibility.
- [ ] Owner completes multi-account online QA after approved backend deployment: matchmaking, private rooms, reconnect, timeout, match history, wallet, friends, blocks, chat, reporting, FCM, leaderboards and tournaments.
- [ ] Verify no offline practice result grants online rating, wallet currency or competitive match history.
- [ ] Run backend load, abuse, transaction contention, cost, quota and failure-recovery tests in an approved staging environment.
- [ ] Verify scheduled functions, IAM, retry/backfill, alerting, log redaction and rollback before production rollout.

## Store listing and review

- [ ] Prepare accurate short/full descriptions and screenshots showing only features available in the submitted build.
- [ ] Complete content rating questionnaire and target audience / age declarations based on actual product design.
- [ ] Complete app access instructions for reviewers if authentication is required; provide dedicated review credentials safely through Play Console, not in source control.
- [ ] Complete ads declaration, Data safety, privacy policy URL, account deletion URL, app category, contact details and all other current Play Console declarations.
- [ ] Verify current Google Play policy and technical requirements at submission time; requirements can change.
- [ ] Prepare release notes, staged rollout percentage, crash/ANR monitoring and rollback owner.

## Owner-only gates

Production Firebase deployment, App Check enforcement, billing activation, real ad account setup, signing-key creation/use, Play Console declarations and publishing require the owner's account access and explicit approval. The assistant must not claim these are complete without evidence.

## Current known gaps

- Account deletion is not implemented end to end; see `ACCOUNT_DATA_LIFECYCLE.md`.
- Privacy notice is a draft and needs owner/legal review; see `PRIVACY_NOTICE_DRAFT.md`.
- AdMob rewarded ads and server-side verification are not implemented.
- Coin spending and a virtual item catalog are not implemented or approved.
- Production Firebase Functions / rules deployment and production App Check enforcement are not verified.
- Physical-device QA and final APK generation are owner-owned tasks.