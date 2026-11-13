# ArenaCards master build status

Updated: 2026-11-13

## Latest requested Android validation

Command:

```powershell
.\gradlew.bat :feature:game:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:profile:testDebugUnitTest :app:assembleDebug
```

Final results after reviewing and fixing the offline game implementation:

- Game engine and ViewModel unit tests: **28 passed**, 0 failures.
- Authentication unit tests: **16 passed**, 0 failures.
- Player profile unit tests: **11 passed**, 0 failures.
- `:app:assembleDebug`: **BUILD SUCCESSFUL**.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (about 19.6 MB).

The Gradle tasks were executed locally with cached JDK 17. The source change is limited to the offline-game feature and documentation; Firebase auth/profile implementations were not changed during this validation pass.

## Backend and production status

- Cloud Functions source and Firestore rules are not deployed.
- Production authentication/profile behavior and real-device gameplay remain unverified.
- No billing plan or paid service was enabled; no production Firebase data was changed.
- Do not infer production readiness from a successful local build or emulator/unit tests.

See [BUILD_STATUS.md](./BUILD_STATUS.md), [REMAINING_WORK.md](./REMAINING_WORK.md), and [PENDING_MANUAL_ACTIONS.md](./PENDING_MANUAL_ACTIONS.md).
