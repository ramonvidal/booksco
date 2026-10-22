# Booksco

A simple, robust, modern podcast player for Android — subscribe, group by genre, sort your way,
and listen at 1x / 1.25x / 1.5x / 2x with reliable background playback.

**Status:** M0 (project setup). See [plan.md](plan.md) for scope, design and roadmap.

## Tech
Kotlin · Jetpack Compose (Material 3) · Hilt · Room · DataStore · Retrofit · AndroidX Media3

## Getting started
1. Install Android Studio (latest stable) with JDK 17+.
2. Open this folder. On first open, if there is no `gradlew`, run `gradle wrapper --gradle-version 8.11.1`
   (or let Android Studio create it) and commit the generated files.
3. Run the `app` configuration on an emulator or device (Android 8.0+).

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

## Working with AI agents
See [AGENTS.md](AGENTS.md) and the skills in [.claude/skills](.claude/skills).
