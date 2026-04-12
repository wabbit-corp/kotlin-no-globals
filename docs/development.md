# Development Guide

This repo contains the full stack for `kotlin-no-globals`:

- a published annotation library
- a Kotlin K2 FIR compiler plugin
- a Gradle plugin that wires the compiler plugin into Kotlin builds

## Repo Layout

- [library](../library/)
  The `one.wabbit:kotlin-no-globals` artifact containing `@RequiresGlobalState`.

- [compiler-plugin](../compiler-plugin/)
  The K2 compiler plugin. Most rule changes land here.

- [gradle-plugin](../gradle-plugin/)
  The Gradle adapter for `id("one.wabbit.no-globals")`.

- [PLAN.md](../PLAN.md)
  Historical checklist of review-driven work that shaped the current implementation.

## Where To Change What

### Change rule semantics

Start in:

- [NoGlobalsCheckersExtension.kt](../compiler-plugin/src/main/kotlin/one/wabbit/noglobals/NoGlobalsCheckersExtension.kt)
- [NoGlobalsConfiguration.kt](../compiler-plugin/src/main/kotlin/one/wabbit/noglobals/NoGlobalsConfiguration.kt)
- [CompilerIntegrationTest.kt](../compiler-plugin/src/test/kotlin/one/wabbit/noglobals/CompilerIntegrationTest.kt)

The tests compile real Kotlin snippets through the actual compiler pipeline. That is the main
confidence source for this repo.

### Change Gradle wiring

Start in:

- [NoGlobalsGradlePlugin.kt](../gradle-plugin/src/main/kotlin/one/wabbit/noglobals/gradle/NoGlobalsGradlePlugin.kt)
- [NoGlobalsGradlePluginTest.kt](../gradle-plugin/src/test/kotlin/one/wabbit/noglobals/gradle/NoGlobalsGradlePluginTest.kt)
- [NoGlobalsGradlePluginFunctionalTest.kt](../gradle-plugin/src/test/kotlin/one/wabbit/noglobals/gradle/NoGlobalsGradlePluginFunctionalTest.kt)

Use the functional tests when changing:

- plugin application
- composite build behavior
- explicit annotation dependency wiring
- multiplatform/native behavior

### Change the marker annotation

Start in:

- [RequiresGlobalState.kt](../library/src/commonMain/kotlin/one/wabbit/noglobals/RequiresGlobalState.kt)

Be careful: changing targets or retention has direct consequences for both the checker and caller
opt-in behavior.

## Testing

Common commands:

```bash
./gradlew :kotlin-no-globals-plugin:test
./gradlew :kotlin-no-globals-gradle-plugin:test
./gradlew :kotlin-no-globals:compileKotlinJvm
```

Useful combinations:

```bash
./gradlew :kotlin-no-globals-plugin:test :kotlin-no-globals-gradle-plugin:test :kotlin-no-globals:compileKotlinJvm
```

The Gradle plugin tests include real TestKit builds. Some functional native checks are slower than
the pure compiler-plugin suite.

## Documentation

The docs are organized around user intent rather than module layout:

- [rules.md](./rules.md) explains the rule model and current boundaries
- [api-reference.md](./api-reference.md) points to generated API docs and public symbols
- [troubleshooting.md](./troubleshooting.md) maps diagnostics to fixes
- [migration.md](./migration.md) records versioning and upgrade expectations

When adding examples to the README, keep them copy-pasteable and add compiler-plugin coverage if the sample represents expected source behavior.

## Local Consumer Testing

Before publication, downstream repos should use a composite build:

```kotlin
pluginManagement {
    includeBuild("../kotlin-no-globals")
}

includeBuild("../kotlin-no-globals")
```

Both are required:

- `pluginManagement.includeBuild(...)` resolves the Gradle plugin ID
- root `includeBuild(...)` lets Gradle substitute the compiler plugin and annotation artifacts

This distinction is covered by functional tests and is easy to forget.

## Versioning Notes

The published artifacts are not all versioned the same way:

- library artifact: base project version
- Gradle plugin artifact: base project version
- compiler plugin artifact: base project version plus Kotlin version suffix

That compiler-plugin suffix matters because compiler plugins are Kotlin-version-sensitive.

## Rule Philosophy

If you are making semantic changes, keep the rule understandable.

Good changes:

- make an existing behavior less surprising
- cover an obvious global mutable state shape with a clear declaration-based rule
- add tests that make scope boundaries explicit

Risky changes:

- chasing initializer types instead of declared types
- recursively inferring hidden heap mutation through arbitrary APIs
- adding exceptions that make it hard to predict what is or is not flagged

When in doubt, prefer a smaller explicit rule plus documentation over a “smart” rule nobody can
reason about.

## Current Constraints

- K2 only
- Gradle-first integration path
- declaration-shape and declared-type driven by design

If a future change needs broader semantic analysis, write the negative tests first and be explicit
about what predictability tradeoff you are accepting.
