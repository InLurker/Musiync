# Repository Guidelines

## Project Structure & Module Organization
- `wear/`: Wear OS app (Kotlin, Jetpack Compose, Hilt, Proto). Code in `wear/src/main/java`, resources in `wear/src/main/res`, proto in `wear/src/main/proto`.
- `app/`: Phone companion app used for data layer integration and testing.
- Libraries: `innertube/`, `kizzy/`, `kugou/`, `lrclib/`, `material-color-utilities/` (shared utilities and data sources).
- Tooling: Gradle config at root (`build.gradle.kts`, `settings.gradle.kts`), lint rules `lint.xml`, CI configs in `.github/`, release assets in `fastlane/`, images in `assets/`.

## Build, Test, and Development Commands
- Build Wear debug APK: `./gradlew :wear:assembleDebug`
- Install on connected Wear device/emulator: `./gradlew :wear:installDebug`
- Run instrumented tests (Wear): `./gradlew :wear:connectedDebugAndroidTest`
- Lint (Android Lint): `./gradlew :wear:lint` (or `./gradlew lint` for all modules)
- Clean build outputs: `./gradlew clean`
- Phone app (if needed): `./gradlew :app:assembleDebug` / `:app:installDebug`

## Coding Style & Naming Conventions
- Language: Kotlin with Jetpack Compose; 4‑space indentation, no tabs.
- Names: `UpperCamelCase` classes/objects; `lowerCamelCase` functions/vars; resource names `snake_case`.
- Files: one public class per file; filename matches class.
- Compose: prefer stateless composables + `@Stable` models; hoist state.
- DI: use Hilt modules; avoid service locators.
- Formatting: use Android Studio’s Kotlin formatter; fix warnings before PR (`./gradlew lint`).

## Testing Guidelines
- Locations: instrumented UI/tests in `wear/src/androidTest/java`; unit tests in `*/src/test/java`.
- Names: suffix with `Test` (e.g., `PlayerViewModelTest`).
- Run: unit `./gradlew :wear:testDebugUnitTest`; instrumented `./gradlew :wear:connectedDebugAndroidTest`.
- Aim for meaningful coverage on new/changed code; mock I/O and keep tests deterministic.

## Commit & Pull Request Guidelines
- Commits: imperative mood, concise scope. Examples: `fix: crash on null album art`, `feat(wear): queue management`.
- PRs: clear description, rationale, and checklist: steps to test, linked issues (`Fixes #123`), screenshots/GIFs for UI, and notes on devices/emulators used.
- Quality gate: run `clean`, `assembleDebug`, tests, and `lint` locally; remove debug logs/secrets. Do not commit keystores; use env vars (`MUSIC_DEBUG_*`).

## Security & Configuration Tips
- Keep `local.properties` and signing files out of VCS; debug signing can use `MUSIC_DEBUG_*` env vars.
- Proto changes in `wear/src/main/proto` regenerate via Gradle; version shared contracts carefully.
