# PodOuvir

A simple, robust, modern podcast player for Android — subscribe, group by genre, sort your way,
and listen at 1x / 1.25x / 1.5x / 2x with reliable background playback.
Free software, coming to F-Droid. English and Portuguese.

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
See [AGENTS.md](AGENTS.md). The work is driven by an orchestrator and three subagents
([.claude/agents](.claude/agents)): `test-planner`, `coder` and `tester`.

```bash
claude --agent orchestrator   # or, in any Claude Code session: "orchestrate the next plan item"
```
