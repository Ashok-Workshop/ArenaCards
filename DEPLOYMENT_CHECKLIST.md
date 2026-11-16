# ArenaCards deployment checklist

## Current deployment state

- [ ] Firebase Functions deployed — **not deployed; requires owner approval**.
- [ ] Firestore rules deployed — **not deployed; requires owner approval**.
- [ ] Billing enabled — **not enabled; do not enable without owner approval**.
- [ ] Production data changed — **no**.
- [ ] AdMob production units enabled — **not configured/enabled**.
- [ ] Google Play release published — **not published**.

## Before any Firebase deployment

- [ ] Confirm Firebase project ID `arenacards-f3279`, Android application ID `com.arenacards.game`, and Android `google-services.json` locally.
- [ ] Confirm the existing Firestore database is `unoashok` in `asia-south1`; do not create or target `(default)`.
- [ ] Review the existing `us-central1` Functions region against Firestore latency/egress and decide on any future coordinated region migration.
- [ ] Review Functions authorization, validation, cost controls, abuse/rate limits, error logging, and secret handling.
- [ ] Decide App Check provider, debug-token handling, enforcement rollout, and reCAPTCHA/Play Integrity requirements.
- [ ] Run Functions tests and Firestore Emulator rules tests against the exact release candidate.
- [ ] Review billing requirements, quotas, budget alerts, retention, backups, rollback, and incident ownership.
- [ ] Obtain explicit owner approval for billing changes and deployment.
- [ ] Deploy reviewed rules/functions to the intended project only after approval; verify deployed versions and test with dedicated accounts.
- [ ] Document rollback procedure and monitor errors, quotas, and unexpected writes after rollout.

## Before any mobile release

- [ ] Complete physical-device auth/gameplay and accessibility testing.
- [ ] Configure release signing outside the repository; verify signing and R8 behavior.
- [ ] Review privacy policy, Data safety, account deletion, age rating, user-generated content, ads/consent disclosures and support contact.
- [ ] Verify versioning, AAB, crash reporting, store assets, release notes, and staged rollout.
- [ ] Obtain explicit approval before publishing or uploading an APK/AAB.
