# Repository Guidelines

## Project Structure & Module Organization
- `wear/`: Wear OS app (Kotlin, Jetpack Compose, Hilt, Proto). Code in `wear/src/main/java`, resources in `wear/src/main/res`, proto in `wear/src/main/proto`.
- `app/`: Phone companion app for data layer integration and local testing.
- Libraries: `innertube/`, `kizzy/`, `kugou/`, `lrclib/`, `material-color-utilities/` (shared utilities and data sources).
- Tooling: root Gradle (`build.gradle.kts`, `settings.gradle.kts`), `lint.xml`, CI in `.github/`, release assets in `fastlane/`, images in `assets/`.
- Tests: instrumented in `wear/src/androidTest/java`; unit tests in `*/src/test/java`.

## Build, Test, and Development Commands
- Build Wear debug APK: `./gradlew :wear:assembleDebug`
- Install on Wear device/emulator: `./gradlew :wear:installDebug`
- Run instrumented tests (Wear): `./gradlew :wear:connectedDebugAndroidTest`
- Run unit tests: `./gradlew :wear:testDebugUnitTest`
- Lint (Wear/all): `./gradlew :wear:lint` or `./gradlew lint`
- Clean outputs: `./gradlew clean`
- Phone app (if needed): `./gradlew :app:assembleDebug` / `:app:installDebug`

## Coding Style & Naming Conventions
- Kotlin, 4‑space indentation; no tabs. One public class per file; filename matches class.
- Names: `UpperCamelCase` for classes/objects; `lowerCamelCase` for functions/vars; resource names `snake_case`.
- Compose: prefer stateless composables and `@Stable` models; hoist state.
- DI: use Hilt modules; avoid service locators.
- Formatting: use Android Studio’s Kotlin formatter; fix warnings before PR; run `./gradlew lint`.

## Testing Guidelines
- Locations: UI/instrumented in `wear/src/androidTest/java`; unit in `*/src/test/java`.
- Names: suffix tests with `Test` (e.g., `PlayerViewModelTest`).
- Run: unit `./gradlew :wear:testDebugUnitTest`; instrumented `./gradlew :wear:connectedDebugAndroidTest`.
- Practice: mock I/O; keep tests deterministic; target meaningful coverage for changes.

## Commit & Pull Request Guidelines
- Commits: imperative mood, concise scope (e.g., `fix: crash on null album art`, `feat(wear): queue management`).
- PRs: clear description, rationale, steps to test, linked issues (e.g., `Fixes #123`), screenshots/GIFs for UI, and devices/emulators used.
- Quality gate: run `clean`, `assembleDebug`, tests, and `lint` locally; remove debug logs/secrets.

## Security & Configuration Tips
- Do not commit `local.properties` or signing files; use `MUSIC_DEBUG_*` env vars for debug signing.
- Proto changes in `wear/src/main/proto` regenerate via Gradle; version shared contracts carefully.

