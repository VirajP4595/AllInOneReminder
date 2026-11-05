# All-in-One Reminder

A family health reminder app for Android: medicines, injections, water, meals, exercise and appointments, with full-screen alarms that ring on a locked phone.

- **Plan and spec:** [`docs/PLAN.md`](docs/PLAN.md)
- **Implementation brief (Phase 1):** [`docs/AGENT_PROMPT.md`](docs/AGENT_PROMPT.md)
- **Decision log:** [`docs/DECISIONS.md`](docs/DECISIONS.md)
- **Device test script:** [`docs/TESTING.md`](docs/TESTING.md)
- **Owner setup (signing, Phase 2 backend):** [`docs/SETUP.md`](docs/SETUP.md)

> Not medical advice. This app reminds; it does not prescribe, calculate doses or check interactions.

---

## Building the project

### Prerequisites
- **JDK 21** (e.g. Eclipse Temurin 21)
- **Android SDK** with:
  - `compileSdk 35`
  - `minSdk 30`
  - `build-tools 35.0.0` or newer

### Build commands
Run all checks (lint, unit tests) and build the debug APK:
```bash
./gradlew lint test assembleDebug
```

On Windows (PowerShell / Command Prompt):
```powershell
.\gradlew.bat lint test assembleDebug
```

### Output artifacts
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint reports: `app/build/reports/lint-results-debug.html`
- Test reports: `app/build/reports/tests/testDebugUnitTest/index.html`
- Code coverage report (core:schedule): `core/schedule/build/reports/jacoco/test/html/index.html`
