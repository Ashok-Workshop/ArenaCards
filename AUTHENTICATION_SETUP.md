# ArenaCards authentication setup

## What is implemented

- Firebase is initialized from the app's generated Google Services resources.
- Email registration records a user-selected display name in Firebase Authentication, requires matching passwords and sends a verification email. Email/password accounts remain restricted until Firebase reports the address verified.
- Email login, password reset with a non-enumerating confirmation, verification resend/refresh, persistent Firebase sessions, and logout use Firebase Authentication.
- Google sign-in uses AndroidX Credential Manager, exchanges the Google ID token with Firebase, and reports account collisions without creating a duplicate account. Signed-in users can link Google from Home > Settings.
- Phone sign-in and phone linking use Firebase Phone Authentication callbacks and Firebase phone credentials. OTP values are transient UI input only; Firebase verification IDs and resend tokens are held in memory and never written to app storage or Firestore. Manual OTP entry remains available if automatic verification is not delivered.
- Phone and Google identities are authenticated by Firebase UID/provider state; they are not blocked by email verification. Email/password identities require verification.
- No Firestore documents, public rules, coins, or multiplayer services are implemented.

## Configuration evidence and status

- `app/google-services.json` contains a client whose Android package matches `com.arenacards.game`.
- The matching client contains OAuth client entries of types 1 and 3. A forced `:app:processDebugGoogleServices` run completed and generated `default_web_client_id`; its value is intentionally not printed here. Google sign-in still requires the Google provider and correct signing fingerprints. This project has not been tested with a real Google account.
- The Firebase Console cannot be inspected from the development environment. The owner reported enabling Google, Email/Password, and Phone, but that status is not independently verified here. Check each provider and the SMS policy directly in the Console.
- Cloud Firestore is not used by this authentication implementation; it is not necessary to enable or create a Firestore database for these flows.

## Firebase Console checklist

In the Firebase Console for the project selected by `app/google-services.json`:

1. Open **Authentication > Sign-in method** and confirm:
   - **Email/Password** is enabled.
   - **Google** is enabled and a support email is selected.
   - **Phone** is enabled.
2. Open **Project settings > General > Your apps > Android app `com.arenacards.game`**. Register the following debug signing fingerprints:
   - SHA-1: `86:9E:8B:43:0C:C8:9C:AE:0D:37:C9:15:4E:85:0B:82:3A:F6:04:44`
   - SHA-256: `33:BB:6B:5C:A1:F6:1B:C8:5F:A2:DB:73:C0:A8:B8:37:81:A2:6F:7D:2B:D6:BF:52:6D:2C:02:63:41:3F:F7:CD`
   These fingerprints are for the local debug key only. Register release fingerprints separately before using a release-signed build.
3. If Google is enabled or fingerprints were added after downloading the config, download the updated `google-services.json` for the same Android app and replace `app/google-services.json`. Do not hand-edit OAuth client IDs. Rebuild and confirm that `default_web_client_id` is generated.
4. Open **Authentication > Settings > SMS region policy**. Restrict sending to regions where ArenaCards is intended to operate. An allowlist can reduce abuse and cost, but must include the regions used for testing.
5. Review the project billing plan and Firebase Authentication phone/SMS pricing and regional availability before production use. SMS quotas, per-number limits, carrier filtering, anti-abuse checks, and billing requirements vary by project, country, and Firebase plan. This implementation does not enable billing or change the project's plan.
6. For development, configure Firebase Authentication's **Phone numbers for testing** with controlled test numbers and the Firebase Console-assigned test codes. Test numbers do not send SMS. Never use a hard-coded test code or bypass Firebase verification in production.

No Firestore rules or database changes are part of this phase. Keep any existing Firestore rules restrictive; do not make a database public.

Account deletion is not implemented. Before adding it, define confirmation and reauthentication UX, delete the Firebase Authentication account through the SDK, and coordinate deletion of any future user data through trusted rules/backend workflows. Do not treat client-only profile cleanup as authoritative.

## Real-device testing

Install the debug APK on Android 7.0 / API 24 or newer with internet access. Check that the Android application ID is `com.arenacards.game`, and test only accounts and phone numbers you control.

### Email and password

1. Confirm the Email/Password provider is enabled.
2. Open ArenaCards > Continue with email > Create a new email account.
3. Enter a username (3-20 letters, numbers, or underscores), accessible email address, password (at least 8 characters with a letter and a number), and matching confirmation.
4. Follow Firebase's verification email link. Return to the app and tap **I've verified my email**.
5. Confirm the verified account reaches the home screen and remains signed in after restarting the app.
6. Sign out, sign in again, and confirm the same Firebase account is restored.
7. Use **Forgot password?** and confirm the reset email arrives. The app deliberately does not reveal whether an arbitrary email has an account.
8. Try a wrong password and an unverified account; verify the UI displays an error or keeps the account on the verification step without entering authenticated routes.

### Google

1. Confirm Google is enabled and both debug fingerprints above are registered. Download the updated configuration, rebuild, and confirm `default_web_client_id` exists.
2. Install on a device with Google Play services and an account you control.
3. Open **Continue with Google**, select an account, and confirm Firebase returns to the signed-in home screen.
4. Confirm the account appears in Firebase Authentication. Test **Home > Settings > Connected sign-in methods > Link Google** with an account that does not already belong to another Firebase user.
5. If the email already belongs to another provider, sign in with that original method and link from the authenticated account. The app does not silently merge or create a duplicate identity.

### Phone OTP and linking

1. Confirm Phone is enabled, the SMS region policy allows the test country, and Firebase phone testing numbers or an appropriate real-device number are configured.
2. Open **Continue with phone**. India (+91) is selected by default; choose the appropriate calling code and enter the national number.
3. For a Console test number, enter the assigned test code. For a real number, wait for the Firebase SMS and enter its six digits; automatic Firebase verification may sign in without manual entry.
4. Test an incorrect/expired code, **Resend code**, the cooldown, airplane mode/network recovery, and a number blocked by the SMS region policy. Firebase quotas may reject repeated requests.
5. To link a phone, sign in first, then open **Home > Settings > Connected sign-in methods > Link a phone number**. Verify that the authenticated account remains the same Firebase UID.
6. If Firebase reports that the phone belongs to another account, sign in with that account's existing method and recover access there. The app will not merge accounts automatically.
7. Confirm logout and session restoration. Never record or share real OTPs in logs, screenshots, issue reports, or chat.

## Validation commands

From PowerShell in the project root:

```powershell
.\gradlew.bat :app:processDebugGoogleServices
.\gradlew.bat :feature:auth:testDebugUnitTest :feature:game:testDebugUnitTest :app:assembleDebug
```

Generated resources and debug APK:

- Resource output: `app/build/generated/res/processDebugGoogleServices/values/values.xml`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

Build and unit tests do not prove that providers are enabled, that SMS delivery works, or that Google/email/phone authentication succeeds on a real device. No real-device authentication test has been run as part of this change.
