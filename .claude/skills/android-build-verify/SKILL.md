---
name: android-build-verify
description: Build, test and lint the PodOuvir Android app and verify a change works. Use before declaring any code change done, when CI fails, or when Gradle/SDK setup is in question.
---

# Build & verify

1. Check tooling: `java -version` (need JDK 17+), `echo $ANDROID_HOME` (Android SDK).
   - No JDK/SDK locally? Do not pretend it built. Say so, and rely on CI (`.github/workflows/ci.yml`).
2. Wrapper: if `gradlew` is absent, `gradle wrapper --gradle-version 8.11.1`.
3. Run, in order, stopping at the first failure:
   ```bash
   ./gradlew assembleDebug
   ./gradlew testDebugUnitTest
   ./gradlew lintDebug
   ```
4. On failure: read the first error (not the last), fix the root cause, re-run that step.
   - "Unresolved reference" after adding a library → check `gradle/libs.versions.toml` and the `dependencies {}` block.
   - KSP/Hilt errors → missing `@HiltAndroidApp`, `@AndroidEntryPoint`, or `@Inject constructor`.
   - Room schema errors → bump DB version and add a migration; schemas are exported to `app/schemas/`.
5. For UI changes, also run on an emulator if available (`./gradlew installDebug`) and check light + dark theme.
6. Report: which commands ran and their result. Never claim green without output.
