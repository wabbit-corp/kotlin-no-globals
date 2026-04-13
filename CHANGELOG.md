# Changelog

All notable changes to this project will be documented in this file.

The format is intentionally simple and release-oriented for now.

## 0.1.1 - 2026-04-13

Patch release for the 0.1 line.

Included in this release:

- `kotlin-no-globals`
- `kotlin-no-globals-gradle-plugin`
- `kotlin-no-globals-plugin`

Highlights:

- fixes the composite-build Gradle-plugin validation path used by release/docs CI
- aligns the IntelliJ support module with the Java 21 / IntelliJ 2025.3 helper baseline
- refreshes pinned documentation examples to the current published version

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
