# Repository Guidelines

## Project Structure & Module Organization
- `wear/`: Kotlin Wear OS app using Jetpack Compose, Hilt, and Proto; source in `wear/src/main/java`, resources in `wear/src/main/res`, proto contracts in `wear/src/main/proto`.
- `app/`: Phone companion app used for data layer integration and local validation; mirrors Wear data contracts.
- Shared libraries (`innertube/`, `kizzy/`, `kugou/`, `lrclib/`, `material-color-utilities/`) expose reusable data sources and utilities.
- Tests live beside modules: unit tests in `*/src/test/java`, instrumented tests under `wear/src/androidTest/java`.
- Tooling and assets: Gradle config at repo root, lint rules in `lint.xml`, CI under `.github/`, imagery in `assets/`, release metadata in `fastlane/`.

## Build, Test, and Development Commands
- `./gradlew :wear:assembleDebug` builds a debuggable Wear APK.
- `./gradlew :wear:installDebug` deploys the Wear app to a connected device or emulator.
- `./gradlew :wear:testDebugUnitTest` runs module unit tests; ensure new logic has coverage.
- `./gradlew :wear:connectedDebugAndroidTest` executes instrumented Compose/UI tests; requires an attached Wear target.
- `./gradlew :wear:lint` (or `./gradlew lint`) enforces static analysis and repository coding standards.
- `./gradlew clean` removes previous build outputs before release or CI runs.

## Coding Style & Naming Conventions
- Kotlin with 4-space indentation; rely on Android Studio formatter before committing.
- Favor stateless composables, hoist state, and annotate stable models with `@Stable` when necessary.
- Name classes/objects in UpperCamelCase, functions and vars in lowerCamelCase, resources in snake_case; limit one public class per file.
- Inject dependencies with Hilt modules; avoid manual service locators or singletons.

## Testing Guidelines
- Follow `FeatureNameTest` naming; mirror package structure inside `src/test` and `src/androidTest`.
- Stub network or file I/O; keep tests deterministic for CI.
- Validate new features with unit tests and targeted UI flows via instrumented tests before submitting.

## Commit & Pull Request Guidelines
- Use imperative, scoped commits (e.g., `feat(wear): queue management`); keep each change focused.
- PRs should describe motivation, testing performed, and link issues (`Fixes #123`).
- Provide screenshots or GIFs for UI changes and note device/emulator coverage.
- Run `clean`, `assembleDebug`, relevant tests, and lint locally before requesting review.

## Security & Configuration Tips
- Never commit signing artifacts or `local.properties`; leverage `MUSIC_DEBUG_*` environment variables.
- Update proto schemas carefully; regenerate bindings via Gradle to keep Wear and phone apps in sync.
