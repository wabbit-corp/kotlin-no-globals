# Migration Guide

This project is experimental and pre-1.0. Treat minor-version changes as potentially breaking until a stable compatibility policy is published.

Current release baseline:

- project version: `0.0.1`
- supported Kotlin lines: `2.3.10`, `2.4.0-Beta1`
- JVM toolchain for library, compiler plugin, and Gradle plugin: JDK 21
- rule model: declaration-shape and declared-type driven

## Upgrade Checklist

1. Update the Gradle plugin version:

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.no-globals") version "0.0.1"
}
```

2. Update the explicit annotation-library dependency to the same base version:

```kotlin
dependencies {
    implementation("one.wabbit:kotlin-no-globals:0.0.1")
}
```

3. Keep the Kotlin Gradle plugin version on a published compiler-plugin line.

4. Run the compiler-plugin checks on every configured Kotlin target.

```bash
./gradlew build
```

## Kotlin Version Changes

The compiler plugin is published with a Kotlin-version suffix:

- `one.wabbit:kotlin-no-globals-plugin:<baseVersion>-kotlin-<kotlinVersion>`

Gradle users normally do not reference this artifact directly. The Gradle plugin derives the right artifact from the applied Kotlin Gradle plugin version.

Direct compiler users must update the suffixed artifact manually when the Kotlin compiler version changes.

## Breaking-Change Areas

The most likely migration points before 1.0 are:

- additions to the default mutable-type blacklist
- changes to diagnostic wording
- changes to which declaration shapes are considered global mutable state
- changes to Gradle DSL defaults
- Kotlin compiler API compatibility changes between supported Kotlin lines

When a change has a mechanical upgrade path, it should be recorded in [`../CHANGELOG.md`](../CHANGELOG.md) and linked back here.
