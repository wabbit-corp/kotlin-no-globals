# API Reference

This page is the stable map for the public surface. Exhaustive API docs are generated from source with Dokka and should be treated as the version-specific reference.

Published API docs:

- `https://wabbit-corp.github.io/kotlin-no-globals/`

Generate local API docs from the repository root:

```bash
./gradlew :kotlin-no-globals:dokkaGeneratePublicationHtml
./gradlew :kotlin-no-globals-gradle-plugin:dokkaGeneratePublicationHtml
./gradlew :kotlin-no-globals-plugin:dokkaGeneratePublicationHtml
./gradlew :kotlin-no-globals-ij-plugin:dokkaGeneratePublicationHtml
```

## Public Annotation API

The annotation API lives in `one.wabbit:kotlin-no-globals`.

- `@RequiresGlobalState` marks a declaration that exposes global mutable state by design.

`@RequiresGlobalState` is also a Kotlin `@RequiresOptIn` marker. Callers must acknowledge the dependency with:

```kotlin
@OptIn(RequiresGlobalState::class)
```

Current targets:

- properties
- classes, for singleton mutable-carrier objects
- functions, for APIs that propagate global-state dependency to callers

## Gradle DSL

The Gradle DSL is exposed by plugin id `one.wabbit.no-globals`.

```kotlin
noGlobals {
    enabled.set(true)
    blacklistedTypes.add("sample.MutableBox")
}
```

Available properties:

- `enabled`: enables or disables checker registration for the module
- `blacklistedTypes`: adds fully qualified type names to the mutable-carrier blacklist

The Gradle plugin also adds the annotation library dependency for supported Kotlin targets.

## Compiler Plugin Options

Direct compiler-plugin users can pass:

```text
-P plugin:one.wabbit.no-globals:enabled=true|false
-P plugin:one.wabbit.no-globals:blacklistedType=com.example.MutableBox
```

`blacklistedType` is repeatable. Malformed entries fail during option processing rather than being ignored.
