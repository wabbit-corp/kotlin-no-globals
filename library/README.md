# kotlin-no-globals Library

This module publishes the `one.wabbit:kotlin-no-globals` annotation artifact.

It contains the public marker annotation:

- `one.wabbit.noglobals.RequiresGlobalState`

## What It Is For

`@RequiresGlobalState` is used to mark declarations that intentionally rely on global mutable state.

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
    implementation("one.wabbit:kotlin-no-globals:<version>")
}
```

For the full rule semantics, see [../docs/rules.md](../docs/rules.md).
