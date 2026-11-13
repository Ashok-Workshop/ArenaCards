# ArenaCards

ArenaCards is a premium Android card game starter project built with Kotlin, Jetpack Compose, and modular project structure.

## Getting started

1. Install Android Studio with the Android SDK.
2. Open the project in Android Studio.
3. Let Gradle sync and accept the SDK licenses.
4. Run the `app` configuration on an emulator or device.

## Highlights

- Jetpack Compose UI foundation
- Modular architecture for future features
- Three-page onboarding with locally persisted completion
- Home dashboard, account UI, game-mode selection, and practice table
- Offline card game with special-card rules, color selection, CPU opponents, and resumable practice
- Navigation and theme system
- System-aware Material 3 light and dark themes

## Phase 2B authentication

The authentication foundation uses Firebase Authentication for email/password registration and login, password reset, email verification, Google sign-in with AndroidX Credential Manager, Firebase Phone OTP, provider linking, persistent sessions, and logout. Email/password accounts must verify their email before entering authenticated routes. Google and phone accounts use Firebase provider state and Firebase UID.

Firebase Console provider status and SMS delivery have not been independently verified. Google sign-in requires valid signing fingerprints and a generated `default_web_client_id`. Follow [AUTHENTICATION_SETUP.md](./AUTHENTICATION_SETUP.md) for Firebase Console setup and real-device test steps.

Online multiplayer, coin balances/transactions, and reward claims are not implemented. Offline game functionality remains local.

## Phase 2C player profiles

The app now includes Firebase UID-keyed player profile setup, username availability feedback, avatar/display-name editing, Gamer ID display/copy, visibility settings, level/stat cards, Firestore cache-backed profile observation, and public player search interfaces.

Gamer ID and username allocation are implemented in trusted Cloud Functions source and protected by Firestore transactions/rules. **Cloud Functions and Firestore rules have not been deployed.** Profile creation and edits therefore require deployment of the reviewed backend before those actions can succeed. No paid services were enabled. Read [PLAYER_PROFILE_SETUP.md](./PLAYER_PROFILE_SETUP.md), [FIRESTORE_DATA_MODEL.md](./FIRESTORE_DATA_MODEL.md), and [FIRESTORE_SECURITY.md](./FIRESTORE_SECURITY.md) before configuring Firebase.

The Firestore Emulator rules test suite and Cloud Functions domain helper tests are available under `firebase/`. Emulator tests do not establish production security or verify deployed Functions.

The offline practice engine uses a 108-card deck and supports two to four seats, matching, Skip, Reverse, Draw Two, Wild, legal Wild Draw Four play, penalties, CPU difficulty selection, pause/resume, and saved match state. Offline results do not grant competitive rewards. Animation/audio polish, durable statistics, full device QA, and all online economy/social/multiplayer features remain outstanding; see [REMAINING_WORK.md](./REMAINING_WORK.md).

## Install a debug build on a phone

1. Enable Developer options and USB debugging on an Android phone (Android 7.0 / API 24 or newer).
2. Connect the phone over USB and accept its debugging prompt.
3. From PowerShell in the project folder, run:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

4. Install the APK with Android Debug Bridge:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"
   ```

Alternatively, open the project in Android Studio, select a connected device, and run the `app` configuration.

## Notes

Firebase Authentication code is connected, but provider configuration and real-device authentication are not independently verified. Profile functions and rules are source-only until owner-approved deployment. The local emulator test toolchain currently reports moderate-severity npm audit findings in development-only dependencies; the Cloud Functions runtime dependencies have no known npm audit findings. Online multiplayer and coin services are not implemented.
