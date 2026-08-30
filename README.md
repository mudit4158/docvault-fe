# DocVault — Android

Native Android client for DocVault, a secure personal document vault. Talks to the FastAPI
backend in `docvault-be`.

> **Working with Claude Code in this repo?** Start at [`CLAUDE.md`](CLAUDE.md).

## Stack

Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Gradle (Kotlin DSL) · minSdk 26 / targetSdk 34

## Setup

Requires JDK 17 and the Android SDK (compileSdk 34). Open the project in Android Studio, or from
the command line:

```bash
./gradlew assembleDebug
./gradlew connectedAndroidTest   # requires a running emulator/device
```

## Layout

```
app/src/main/java/com/docvault/app/
├── DocVaultApplication.kt   # Application class
├── MainActivity.kt          # single Activity, hosts the Compose nav graph
├── navigation/              # DocVaultDestination (tab enum) + DocVaultNavHost
├── ui/theme/                # Color.kt, Type.kt, Theme.kt — dark-first Material 3 theme
├── ui/components/           # shared composables (e.g. DocVaultBottomBar)
└── ui/screens/<feature>/    # one package per tab: vault, scan, groups, me
```

Each feature screen owns its own package under `ui/screens/`. As a feature grows past a single
composable (its own nav sub-graph, view models, network calls), add `viewmodel/`, `data/`, or a
nested `navigation/` under that feature's package rather than growing this shell.

## Current State

Scaffold only: four-tab bottom nav (Vault, Scan, Groups, Me) wired to a `NavHost`, each tab a
placeholder screen, dark-first Material 3 theme matching the v2 prototype's accent color. No
auth, networking, or persistence yet — see the sprint plan for build order.

## Product Reference

Screens and behaviour are specified in the PRD (v1.3), the 7-day sprint plan, and the engineering
handoff doc (`Mobile app prototype scope.pdf`) at the repo root's parent directory — the handoff
doc's §2 data-model-implications table and §3 flow-by-flow reference are the primary source for
entity shapes and per-screen UI states.
