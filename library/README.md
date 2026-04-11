# kotlin-no-globals Library

This module publishes the `one.wabbit:kotlin-no-globals` annotation artifact.

It contains the public marker annotation:

- `one.wabbit.noglobals.RequiresGlobalState`

Start with the [root README](../README.md) for the overview, [rule guide](../docs/rules.md) for semantics, and [API reference](../docs/api-reference.md) for the public surface.

## Status

This module is pre-1.0 and follows the repository Kotlin compatibility matrix in [`../gradle.properties`](../gradle.properties).

## What It Is For

`@RequiresGlobalState` is used to mark declarations that rely on global mutable state by design.

It is also a real Kotlin `@RequiresOptIn` marker, so callers must acknowledge the dependency with:

```kotlin
@OptIn(RequiresGlobalState::class)
```

## Typical Usage

```kotlin
import one.wabbit.noglobals.RequiresGlobalState

@RequiresGlobalState
var counter: Int = 0
```

The compiler plugin requires this annotation for declarations it considers global mutable state.

## Installation

Most users get this module automatically by applying the Gradle plugin, but manual setup is still straightforward:

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.no-globals") version "0.0.1"
}

dependencies {
    implementation("one.wabbit:kotlin-no-globals:0.0.1")
}
```

Run `./gradlew compileKotlin` after adding the dependency to confirm the annotation resolves in source.

## Targets

The annotation currently targets:

- `PROPERTY`
- `CLASS`
- `FUNCTION`

That allows:

- properties to opt in directly
- singleton mutable-carrier objects to opt in at the class declaration
- helper functions to propagate explicit opt-in through APIs when desired

## When You Need This Module Directly

If you apply the Gradle plugin `one.wabbit.no-globals`, this dependency is added automatically for
supported Kotlin targets.

If you are wiring the compiler plugin manually, or you only want the annotation without the Gradle
plugin, add:

```kotlin
dependencies {
    implementation("one.wabbit:kotlin-no-globals:0.0.1")
}
```

Release notes live in [`../CHANGELOG.md`](../CHANGELOG.md). If you hit setup or diagnostic issues, start with [`../docs/troubleshooting.md`](../docs/troubleshooting.md) and the contribution/support guidance in the [root README](../README.md).
