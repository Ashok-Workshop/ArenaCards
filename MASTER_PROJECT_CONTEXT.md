# ArenaCards master project context

Updated: 2026-11-13

## Project

ArenaCards is an existing modular Android card game. Continue in this repository; do not recreate the project, remove working features, replace the game engine, deploy Firebase, enable billing, or change production data without explicit approval.

- Android application ID: `com.arenacards.game`
- Firebase project reported by the owner: `arenacards-f3279`
- Functions source region: `us-central1`
- GitHub: private repository `https://github.com/Ashok-Workshop/ArenaCards`, branch `main`
- Kotlin, Jetpack Compose, Material 3, Firebase Authentication, Firestore, and callable Functions source

## Implemented foundations

- Modular Android project, theme/design system, onboarding, dashboard, navigation, auth UI and Firebase Authentication source.
- Google, email/password, email verification, password recovery, phone OTP, provider linking, persisted Firebase sessions, and logout.
- Firebase UID-keyed player profile UI and repository, profile visibility, usernames, Gamer IDs, Firestore rules, and profile callable Functions source.
- Offline CPU practice now has a standard 108-card deck, two-to-four seats, color/value matching, Skip, Reverse, Draw Two, Wild, Wild Draw Four eligibility and penalties, scoring, win/draw handling, discard reshuffling, three CPU difficulty options, pause/resume, and `SavedStateHandle` restoration.
- Offline games do not award competitive coins or online results.

## Verified boundaries

- Firebase Cloud Functions and Firestore rules are source only and have not been deployed.
- No billing or production Firebase services were enabled, and no production data was changed.
- `app/google-services.json` and local `local.properties` are intentionally ignored by Git. A new clone needs the owner’s local Firebase config and Android SDK setup.
- No real-device auth, gameplay, or production Firebase integration test has been performed.
- Review of source/test code does not establish production readiness or guarantee backend security after deployment.

## Modules and key locations

- `app`: Android entry point and navigation integration.
- `core/designsystem`, `core/navigation`: shared Compose theme/components and destinations.
- `feature/auth`, `feature/profile`, `feature/home`, `feature/game`: feature UI/state and unit tests.
- `data`: Firebase authentication and profile repositories.
- `firebase/functions`: trusted profile/username/Gamer ID callable Functions source.
- `firestore.rules`, `firebase/tests`: restrictive rules source and Firestore Emulator tests.
- `BUILD_STATUS.md`, `REMAINING_WORK.md`, and this directory’s master/handover documents: current state and follow-up actions.

See [README.md](./README.md), [AUTHENTICATION_SETUP.md](./AUTHENTICATION_SETUP.md), [PLAYER_PROFILE_SETUP.md](./PLAYER_PROFILE_SETUP.md), [FIRESTORE_DATA_MODEL.md](./FIRESTORE_DATA_MODEL.md), and [FIRESTORE_SECURITY.md](./FIRESTORE_SECURITY.md) for detailed setup and design.

## Immediate development priority

Finish offline game experience validation: verify edge rules on device, improve responsive/accessibility/animation feedback, and decide whether to add persistent local statistics. Keep all offline results noncompetitive. Defer rewards, ads, multiplayer, and social/chat until trusted backend design, abuse controls, privacy policy, and an owner-approved deployment plan are ready.
