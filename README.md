# kotlin-no-globals

`kotlin-no-globals` is a Kotlin compiler plugin that rejects global mutable state unless the
declaration is explicitly marked with `@RequiresGlobalState`.

The current checker flags:

- top-level `var`
- `lateinit var`
- mutable properties declared in `object` singletons, including companion objects
- mutable properties declared in enum classes and enum entry bodies
- top-level and singleton `val`s whose type is on the mutable-type blacklist
- top-level and singleton `val`s holding anonymous objects with mutable members

Once a property is annotated with `@RequiresGlobalState`, normal Kotlin opt-in rules apply and
callers must acknowledge it with `@OptIn(RequiresGlobalState::class)`.

## Usage

Apply the Gradle plugin:

```kotlin
plugins {
    id("one.wabbit.no-globals")
}
```

Optional Gradle configuration:

```kotlin
noGlobals {
    enabled.set(false)
    blacklistedTypes.add("sample.MutableBox")
}
```

Default blacklist entries include common mutable carriers such as:

- `kotlin.collections.MutableList`
- `kotlin.collections.MutableSet`
- `kotlin.collections.MutableMap`
- `java.util.concurrent.atomic.AtomicInteger`

Add the marker annotation library:

```kotlin
dependencies {
    implementation("one.wabbit:kotlin-no-globals:0.0.1")
}
```

Annotate the declaration:

```kotlin
import one.wabbit.noglobals.RequiresGlobalState

@RequiresGlobalState
var counter: Int = 0
```

Opt in at the use site:

```kotlin
import one.wabbit.noglobals.RequiresGlobalState

@OptIn(RequiresGlobalState::class)
fun readCounter(): Int = counter
```

## Local Composite Builds

Before this project is published, local consumers need both forms of included build wiring:

```kotlin
pluginManagement {
    includeBuild("../kotlin-no-globals")
}

includeBuild("../kotlin-no-globals")
```

The first resolves the Gradle plugin ID. The second allows Gradle to substitute the compiler
plugin artifact on `kotlinCompilerPluginClasspath`.
