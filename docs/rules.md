# Rule Semantics

This document describes what `kotlin-no-globals` is trying to enforce today.

At a glance:

- global `var` is bad by default
- stored global mutable carriers are bad by default
- explicit opt-in is the escape hatch
- the rule is based on declaration shape and declared types, not whole-program alias analysis

## Core Model

The plugin uses `@RequiresGlobalState` as both:

- the marker that blesses a global mutable declaration
- the opt-in requirement that callers must acknowledge

That keeps the declaration site and use site aligned:

```kotlin
@RequiresGlobalState
var counter: Int = 0

@OptIn(RequiresGlobalState::class)
fun readCounter(): Int = counter
```

## Rejected Shapes

### Global `var`

Rejected:

```kotlin
var counter = 0
lateinit var service: Service
```

Also rejected in singleton-like scopes:

```kotlin
object Globals {
    var counter = 0
}

class Globals {
    companion object {
        var counter = 0
    }
}
```

### Enum Mutable State

Rejected:

```kotlin
enum class Mode {
    A;

    var counter: Int = 0
}
```

and:

```kotlin
enum class Mode {
    A {
        var counter: Int = 0
    }
}
```

### Stored Mutable `val`

Rejected when the stored declaration type is blacklisted:

```kotlin
val users = mutableListOf<String>()
val nextId = java.util.concurrent.atomic.AtomicLong(1L)
val state = kotlinx.coroutines.flow.MutableStateFlow(0)
```

Delegated stored state is also rejected:

```kotlin
val users: MutableList<String> by lazy { mutableListOf() }
```

### Singleton Objects That Are Mutable Carriers

Rejected:

```kotlin
object Users : MutableList<String> by mutableListOf()
```

This is important because the singleton itself is the mutable global state, even if no explicit
property declaration exists.

### Anonymous Object Holders

Rejected:

```kotlin
val holder =
    object {
        var counter: Int = 0
    }
```

This also applies in singleton scope.

## Allowed Shapes

### Instance State

Allowed:

```kotlin
class Box {
    var counter: Int = 0
}
```

Instance state is not global state.

### Local State

Allowed:

```kotlin
fun render(): Int {
    var counter = 0
    counter += 1
    return counter
}
```

### Pure Computed `val`

Allowed:

```kotlin
val users: MutableList<String>
    get() = mutableListOf()
```

Why: there is no stored global backing state here. The property returns a fresh mutable value on
each access.

The plugin currently treats a `val` as a pure computed property when it has:

- no initializer
- no delegate
- an explicit getter

### Immutable Public Contract

Allowed:

```kotlin
val users: List<String> = mutableListOf()
```

This is a design choice. The plugin follows the declared type, not the initializer
type. It does not attempt to prove hidden mutability behind an immutable-looking API.

## Annotation Rules

### Properties

Detected mutable properties must be annotated on the property itself:

```kotlin
@RequiresGlobalState
var counter = 0
```

Setter-only or getter-level marking does not satisfy the checker.

### Singleton Mutable Carrier Objects

Mutable singleton objects can be blessed on the class declaration:

```kotlin
@RequiresGlobalState
object Users : MutableList<String> by mutableListOf()
```

### Functions

The checker does not require function annotations by default, but `FUNCTION` is a valid target so
you can manually propagate opt-in through helper APIs:

```kotlin
@RequiresGlobalState
fun allocateId(): Long = nextId.getAndIncrement()
```

## Default Blacklist Philosophy

The default blacklist tries to catch carriers that almost always represent shared mutable state in
practice:

- mutable collections
- atomics
- locks and coordination primitives
- mutable flows and channels
- common mutable builders

User-supplied blacklist entries extend the defaults rather than replacing them.

The checker also matches subtypes of blacklisted types.

## Intentional Non-Goals

The plugin does **not** currently try to catch every possible shape of hidden mutability.

Examples that stay out of scope:

- upcasted initializer tricks such as `List = mutableListOf()`
- anonymous objects hidden behind wrapper expressions like `run { object { ... } }`
- inherited mutable members on anonymous objects
- arbitrary transitive heap mutation hidden behind opaque APIs

Those cases are real, but chasing them aggressively would make the rule harder to predict and much
more expensive to maintain.

## Escape Hatches

Preferred escape hatch:

- `@RequiresGlobalState` on the declaration

Also supported:

- `@Suppress("GLOBAL_MUTABLE_STATE_REQUIRES_OPT_IN")`

Use suppression sparingly. The plugin is most valuable when the codebase uses the explicit marker
consistently.
