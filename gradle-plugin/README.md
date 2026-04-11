# kotlin-no-globals Gradle Plugin

This module provides the Gradle integration for `kotlin-no-globals`.

If you want compile-time enforcement that makes global mutable state explicit in a normal Gradle build, start here. The [root README](../README.md), [rule guide](../docs/rules.md), and [API reference](../docs/api-reference.md) cover the broader model.

## Status

This module is pre-1.0 and tracks the repository Kotlin compatibility matrix.

## Plugin Coordinates

```text
one.wabbit.no-globals
```

Artifact:

```text
one.wabbit:kotlin-no-globals-gradle-plugin:0.0.1
```

## What It Does

When applied to a Kotlin project, the Gradle plugin:

- adds the matching compiler-plugin artifact to Kotlin compilations
- exposes the `noGlobals {}` DSL
- automatically adds the annotation library dependency for supported Kotlin targets

## Quick Start

Use the normal Gradle plugin and dependency repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.no-globals") version "0.0.1"
}
```

```kotlin
repositories {
    mavenCentral()
}

noGlobals {
    enabled.set(true)
    blacklistedTypes.add("sample.MutableBox")
}
```

The plugin adds `one.wabbit:kotlin-no-globals:0.0.1` automatically for supported Kotlin targets, so most builds do not need to declare the annotation dependency separately.

To verify the plugin is active, add a top-level `var` without `@RequiresGlobalState` and run `./gradlew compileKotlin`. The build should fail with the plugin's global-state diagnostic.

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

Release notes live in [`../CHANGELOG.md`](../CHANGELOG.md). For support, troubleshooting, and contribution guidance, start with [`../docs/troubleshooting.md`](../docs/troubleshooting.md) and the [root README](../README.md).
