# kotlin-no-globals

`kotlin-no-globals` is a Kotlin K2 compiler-plugin stack for making global mutable state explicit.

## Quick Start

For a JVM project, the smallest useful setup is:

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

rootProject.name = "no-globals-quickstart"
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.10"
    application
    id("one.wabbit.no-globals") version "0.1.1"
}

dependencies {
    implementation("one.wabbit:kotlin-no-globals:0.1.1")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "sample.MainKt"
}

noGlobals {
    enabled.set(true)
}
```

Then add `src/main/kotlin/sample/Main.kt`:

<!-- quickstart-source:start -->
```kotlin
package sample

import one.wabbit.noglobals.RequiresGlobalState

@RequiresGlobalState
var counter: Int = 0

@RequiresGlobalState
@OptIn(RequiresGlobalState::class)
fun nextCounter(): Int {
    counter += 1
    return counter
}

@OptIn(RequiresGlobalState::class)
fun main() {
    println(nextCounter())
}
```
<!-- quickstart-source:end -->

Run it:

```bash
./gradlew run
```

Expected output:

```text
1
```

Remove `@RequiresGlobalState` from `counter` to see the compiler reject the global `var`. Remove the `@OptIn` markers to see Kotlin's normal opt-in checks reject use of the global-state API.

Instead of silently allowing top-level mutation and singleton-backed mutable state, it requires
those declarations to be marked with `@RequiresGlobalState`. Because that marker is a real Kotlin
`@RequiresOptIn` annotation, every caller must then acknowledge the dependency explicitly with
`@OptIn(RequiresGlobalState::class)`.

The stack combines:

- a small published annotation library with `@RequiresGlobalState`
- a K2 FIR compiler plugin that detects global mutable state
- a Gradle plugin that wires the compiler plugin into Kotlin builds

## Why This Exists

Global mutable state is sometimes necessary, but it is rarely harmless.

It tends to blur ownership, complicate testing, make initialization order matter, and create
surprising transitive dependencies between otherwise ordinary APIs. Kotlin makes these patterns easy
to write; `kotlin-no-globals` makes them visible and explicit.

The goal is not to prove “all hidden mutability everywhere.” The goal is to put a hard compiler
boundary around the most important and predictable forms of global mutable state, and to make the
remaining exceptions obvious in source.

## Status

This repository is experimental and pre-1.0.

- The compiler plugin is K2-only.
- The current Kotlin compatibility matrix is driven by `supportedKotlinVersions` in [`gradle.properties`](./gradle.properties), currently `2.3.10` and `2.4.0-Beta1`.
- The Gradle and compiler-plugin builds target JDK 21.
- The rule is declaration-shape and declared-type driven, not whole-program mutability analysis.

## Modules

| Module | Gradle project | Purpose |
| --- | --- | --- |
| [`library/`](./library/) | `:kotlin-no-globals` | Published annotation artifact containing `@RequiresGlobalState` |
| [`compiler-plugin/`](./compiler-plugin/) | `:kotlin-no-globals-plugin` | K2 FIR compiler plugin that reports diagnostics |
| [`ij-plugin/`](./ij-plugin/) | `:kotlin-no-globals-ij-plugin` | IntelliJ IDEA helper plugin for external compiler-plugin loading |
| [`gradle-plugin/`](./gradle-plugin/) | `:kotlin-no-globals-gradle-plugin` | Gradle integration for `id("one.wabbit.no-globals")` |

## What It Flags

The current rule set is narrow and predictable.

Rejected by default:

- top-level `var`
- `lateinit var`
- `var` declared inside `object` singletons, including companion objects and `data object`
- mutable properties declared in enum classes and enum entry bodies
- top-level and singleton stored `val`s whose declared type is on the mutable-type blacklist
- object singletons whose own type is on the mutable-type blacklist
- top-level and singleton `val`s holding anonymous objects with mutable members

Explicitly allowed:

- instance properties on ordinary classes
- local variables and local object expressions
- pure computed `val`s with an explicit getter and no initializer or delegate
- values whose public declared type is not blacklisted, even if the initializer is more concrete

For the exact semantics and edge cases, see [docs/rules.md](./docs/rules.md).

## Configuration

Gradle DSL:

```kotlin
noGlobals {
    enabled.set(true)
    blacklistedTypes.add("sample.MutableBox")
}
```

Available options:

- `enabled`: turn checking on or off for the current module
- `blacklistedTypes`: extend the default mutable-type blacklist with additional fully qualified type names

Invalid blacklist entries fail fast during compiler option processing.

## Default Blacklist

The built-in blacklist covers common stored mutable carriers:

- Kotlin mutable collection interfaces such as `MutableCollection`, `MutableList`, `MutableSet`, `MutableMap`
- JDK atomics and atomic arrays
- JDK concurrent accumulators, latches, barriers, semaphores, phasers, and locks
- Kotlin stdlib atomics
- `kotlinx.coroutines` mutable flows, `Mutex`, coroutine `Semaphore`, and `Channel`
- `kotlinx.atomicfu` atomics
- mutable builders such as `StringBuilder` and `StringBuffer`

The checker also matches subtypes of those carriers.

For the exact current list, see
[`NoGlobalsConfiguration.kt`](./compiler-plugin/src/main/kotlin/one/wabbit/noglobals/NoGlobalsConfiguration.kt).

## Artifact Coordinates

Most consumers only need the annotation library and the Gradle plugin.

| Module | Coordinates | Role |
| --- | --- | --- |
| Annotation library | `one.wabbit:kotlin-no-globals` | `@RequiresGlobalState` for source code |
| Gradle plugin | `one.wabbit:kotlin-no-globals-gradle-plugin` | Gradle wiring for the compiler plugin |
| Compiler plugin | `one.wabbit:kotlin-no-globals-plugin:<baseVersion>-kotlin-<kotlinVersion>` | Kotlin-line-specific K2 compiler plugin implementation |

The Gradle plugin selects the matching compiler-plugin artifact automatically.

## Kotlin Compatibility And Versioning

The compiler plugin is versioned per Kotlin compiler line:

- `one.wabbit:kotlin-no-globals-plugin:<baseVersion>-kotlin-<kotlinVersion>`

That suffix matters. Compiler plugins are Kotlin-version-sensitive.

The library and Gradle plugin use the base project version, while the compiler plugin appends the
Kotlin line it was built for.

## Direct Compiler Usage

If you are not using Gradle, wire the compiler plugin directly:

```text
-Xplugin=/path/to/kotlin-no-globals-plugin.jar
-P plugin:one.wabbit.no-globals:enabled=true|false
-P plugin:one.wabbit.no-globals:blacklistedType=com.example.MutableBox
```

If source code uses `@RequiresGlobalState`, the annotation library still needs to be on the
compilation classpath.

For compiler-plugin-specific details, see
[`compiler-plugin/README.md`](./compiler-plugin/README.md).

## Local Composite Builds

Before publication, or when testing locally across repositories, consumers need both forms of
composite-build wiring:

```kotlin
pluginManagement {
    includeBuild("../kotlin-no-globals")
}

includeBuild("../kotlin-no-globals")
```

The first resolves the Gradle plugin ID. The second allows Gradle to substitute the compiler
plugin and annotation artifacts.

## Documentation Map

- Published API docs: `https://wabbit-corp.github.io/kotlin-no-globals/`
- [CHANGELOG.md](./CHANGELOG.md): release-oriented history for public versions
- [docs/rules.md](./docs/rules.md): exact rule semantics, examples, and intentional non-goals
- [docs/architecture.md](./docs/architecture.md): module relationships, configuration flow, and enforcement model
- [docs/api-reference.md](./docs/api-reference.md): public API inventory and Dokka generation commands
- [docs/migration.md](./docs/migration.md): versioning policy, compatibility notes, and upgrade checklist
- [docs/troubleshooting.md](./docs/troubleshooting.md): common diagnostics, causes, and fixes
- [docs/development.md](./docs/development.md): local build, testing, versioning, and contribution-oriented notes
- [library/README.md](./library/README.md): annotation artifact usage
- [compiler-plugin/README.md](./compiler-plugin/README.md): direct compiler integration and compiler-side behavior
- [ij-plugin/README.md](./ij-plugin/README.md): IntelliJ IDEA integration for external compiler-plugin loading
- [gradle-plugin/README.md](./gradle-plugin/README.md): Gradle DSL, installation, and local composite build notes
- [PLAN.md](./PLAN.md): review-driven implementation checklist and policy history

## Build And Test

Common commands from the repo root:

```bash
./gradlew build
./gradlew :kotlin-no-globals-plugin:test
./gradlew :kotlin-no-globals-gradle-plugin:test
./gradlew :kotlin-no-globals:compileKotlinJvm
```

The Gradle plugin suite includes real TestKit builds, including native functional coverage, so it
is slower than the pure compiler-plugin test suite.

## Contributing And Licensing

- License: [LICENSE.md](./LICENSE.md)
- Code of conduct: [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md)
- CLA: [CLA.md](./CLA.md)
- Contributor privacy notice: [CONTRIBUTOR_PRIVACY.md](./CONTRIBUTOR_PRIVACY.md)
