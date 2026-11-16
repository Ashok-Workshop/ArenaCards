# ArenaCards security checklist

Updated: 2026-11-14. This is a defensive readiness checklist, not a certification or penetration-test result.

## Present source controls

- [x] Firebase Authentication UID is the profile identity.
- [x] Profile mutation uses callable Functions source rather than Android client writes.
- [x] Gamer ID and normalized username reservations are backend-owned/transactional in source.
- [x] Firestore rules deny client profile/index writes and limit profile reads.
- [x] Offline matches do not grant competitive currency/results.
- [x] Previous source/test review found no actionable vulnerabilities in reviewed code; production backend/configuration was not verified.

## Remaining before production

- [ ] Deploy no Firebase function/rule until explicit owner approval.
- [ ] Configure, test, then enforce Firebase App Check with a controlled debug-token process.
- [ ] Verify authorization and input validation for every callable function, including abuse limits.
- [ ] Add rate limiting/abuse monitoring and audit logging for sensitive account operations.
- [ ] Define and test account deletion, data retention, profile visibility, and recovery flows.
- [ ] Keep local Firebase config, OAuth secrets, signing material, service-account keys and tokens out of Git.
- [ ] Verify rules and Functions together against emulator tests and dedicated Firebase test accounts.
- [ ] Design server-owned, atomic/idempotent wallet operations before implementing rewards.
- [ ] Design server-authoritative game state/results before competitive match rewards or rankings.
- [ ] Define block/report, chat moderation, retention and admin authorization before social launch.
- [ ] Test cross-account reads/writes, account collisions, duplicate requests/rewards, replay and rate-limit behavior.
- [ ] Review dependency advisories and operational logs without logging OTPs, passwords, tokens, or private contact details.

Do not treat passing unit/emulator tests or a source review as proof of production security. Residual risks include unreviewed deployment configuration, abuse patterns, real-device integration, operational monitoring, and future features not yet implemented.
