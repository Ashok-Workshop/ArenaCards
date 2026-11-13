# ArenaCards pending manual actions

Updated: 2026-11-13

These are owner actions. No Firebase deployment, billing activation, or production-data changes have been performed.

## Before building from a fresh clone

- Install Android Studio and the project-required Android SDK/platform tools.
- Provide the Firebase Android configuration file locally at `app/google-services.json`. It is intentionally excluded from Git; do not commit it.
- Configure local SDK paths in `local.properties` as appropriate for the machine; this file is ignored.

## Firebase Console and production verification

- In Firebase project `arenacards-f3279`, confirm Authentication providers (Google, Email/Password, Phone), email verification behavior, Google OAuth/SHA fingerprints, phone SMS region policy, quotas, and test numbers.
- Confirm/create Firestore in the intended region and verify the configured callable Functions region (`us-central1`) matches the planned data location.
- Review username moderation and Cloud Functions source, App Check setup/enforcement plan, Firestore rules, quotas, monitoring, abuse controls, and backup/recovery expectations.
- Cloud Functions and Firestore rules are not deployed. Deployment and any billing upgrade require separate explicit approval. Do not deploy from local testing.
- After approved deployment, test new/existing profiles and auth flows using dedicated test accounts; verify denied cross-user reads/writes and account deletion/recovery requirements.

## Device and release QA

- Test the debug APK on physical Android devices and different screen sizes: two/three/four-player practice, special cards, exhausted draw/discard piles, pause/resume, process death/restoration, wild color picker, accessibility, and orientation/background transitions.
- Real Google, email, phone-SMS, and production profile flows have not been verified by this work.
- Before any Play release, owner decisions/configuration are still needed for release signing, privacy policy, data-safety declarations, account deletion, age rating, user-generated content policies, advertising disclosure, listing assets, and release approval.
- Never share or commit signing keys/passwords or Firebase service-account credentials.
