# Changelog

All notable changes to this project will be documented in this file.

The format is intentionally simple and release-oriented for now.

## 0.0.1 - 2026-04-07

Initial public release.

Included in this release:

- `kotlin-no-globals`: Kotlin Multiplatform annotation library for making global mutable state explicit
- `kotlin-no-globals-gradle-plugin`: typed Gradle plugin for `one.wabbit.no-globals`
- `kotlin-no-globals-plugin`: Kotlin-line-specific K2/FIR compiler plugin artifacts

Highlights:

- explicit `@RequiresGlobalState` marker backed by Kotlin's normal opt-in machinery
- Gradle plugin auto-wiring for the annotation dependency and compiler-plugin artifact
- declaration-shape and declared-type driven enforcement for predictable global-state diagnostics
- configurable mutable-type blacklist with sane defaults for common mutable carriers
- Kotlin publish matrix driven by `supportedKotlinVersions`
