# kotlin-no-globals Gradle Plugin

This module provides the Gradle integration for `kotlin-no-globals`.

Plugin ID:

```text
one.wabbit.no-globals
```

## What It Does

When applied to a Kotlin project, the Gradle plugin:

- adds the matching compiler-plugin artifact to Kotlin compilations
- exposes the `noGlobals {}` DSL
- automatically adds the annotation library dependency for supported Kotlin targets

## Basic Usage

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.no-globals") version "<version>"
}
```

```kotlin
noGlobals {
    enabled.set(true)
    blacklistedTypes.add("sample.MutableBox")
}
```

## DSL

Available properties:

- `enabled`
  - default: `true`
- `blacklistedTypes`
  - default: empty list
  - extends the built-in blacklist rather than replacing it

## Automatic Annotation Dependency

The plugin automatically adds the `one.wabbit:kotlin-no-globals` library dependency for supported
Kotlin plugin setups so `@RequiresGlobalState` is available without separate manual wiring.

That includes:

- Kotlin Multiplatform via `commonMainImplementation`
- Kotlin JVM / Android / JS single-platform builds via `implementation`

## Local Composite Builds

For local use before publication, or for testing against a sibling repository checkout, consumers
need both forms of included build wiring:

```kotlin
pluginManagement {
    includeBuild("../kotlin-no-globals")
}

includeBuild("../kotlin-no-globals")
```

Why both:

- `pluginManagement.includeBuild(...)` resolves the Gradle plugin ID
- root `includeBuild(...)` substitutes the compiler-plugin and annotation artifacts

Using only the first is not enough.

## Functional Coverage

This module has real Gradle TestKit coverage for:

- compiler-plugin activation in a real build
- auto-added annotation dependency behavior
- local composite-build substitution
- native declaration-level opt-in behavior

For repo-wide usage and semantics, see [../README.md](/Users/wabbit/ws/datatron/kotlin-no-globals/README.md).
