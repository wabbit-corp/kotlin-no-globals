# Troubleshooting

This page maps common failures to the rule that caused them and the usual fix.

## A Top-Level `var` Is Rejected

Cause:

- top-level mutable storage is global mutable state

Fix:

- move the state behind an instance
- make it a local variable
- or mark it explicitly:

```kotlin
import one.wabbit.noglobals.RequiresGlobalState

@RequiresGlobalState
var counter: Int = 0
```

Callers must then use Kotlin opt-in:

```kotlin
@OptIn(RequiresGlobalState::class)
fun readCounter(): Int = counter
```

## A Stored `val` Is Rejected

Cause:

- the declared type is a mutable carrier, such as `MutableList`, an atomic, a lock, a mutable flow, or a user-supplied blacklisted type

Fix:

- expose an immutable declared type when the public contract is immutable
- use a computed property with no backing storage when each access creates a fresh value
- or mark the property with `@RequiresGlobalState`

## A Singleton Object Is Rejected

Cause:

- an `object`, companion object, data object, enum, or enum entry owns mutable state
- the singleton itself implements or delegates to a blacklisted mutable carrier

Fix:

- move the mutable state into ordinary instances
- or mark the singleton declaration with `@RequiresGlobalState` when global state is intentional

## The Annotation Is On The Wrong Element

Cause:

- property-level global state must be annotated on the property, not only on an accessor

Fix:

```kotlin
@RequiresGlobalState
var counter: Int = 0
```

Setter-only or getter-only marker placement does not satisfy the checker.

## Callers Still Fail After The Declaration Is Annotated

Cause:

- `@RequiresGlobalState` is a real Kotlin opt-in marker
- marking a declaration makes the declaration legal, but use sites still need to acknowledge it

Fix:

- add `@OptIn(RequiresGlobalState::class)` to the narrowest caller
- or annotate a helper API with `@RequiresGlobalState` to propagate the requirement outward

## No Diagnostics Are Reported

Cause:

- `noGlobals.enabled` may be set to `false`
- the Gradle plugin may not be applied to the Kotlin target you are compiling
- direct compiler wiring may be missing `-Xplugin`

Fix:

```kotlin
noGlobals {
    enabled.set(true)
}
```

For local composite builds, include the build in both plugin management and normal dependency resolution:

```kotlin
pluginManagement {
    includeBuild("../kotlin-no-globals")
}

includeBuild("../kotlin-no-globals")
```

## A Custom Blacklist Entry Fails

Cause:

- blacklist entries must be valid fully qualified type names

Fix:

```kotlin
noGlobals {
    blacklistedTypes.add("sample.MutableBox")
}
```

Malformed entries fail fast so configuration mistakes do not silently weaken the rule.
