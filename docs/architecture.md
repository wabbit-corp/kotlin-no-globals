# Architecture

This document describes how the `kotlin-no-globals` stack fits together and where enforcement
actually happens.

## High-Level Structure

The repository has three runtime-relevant pieces:

| Piece | Module | Responsibility |
| --- | --- | --- |
| Annotation library | [`library/`](../library/) | Publishes `@RequiresGlobalState` |
| Compiler plugin | [`compiler-plugin/`](../compiler-plugin/) | Detects global mutable state and reports diagnostics |
| Gradle plugin | [`gradle-plugin/`](../gradle-plugin/) | Wires the compiler plugin and annotation dependency into Gradle Kotlin compilations |

## Core Enforcement Model

The design reuses Kotlin’s built-in opt-in machinery instead of reinventing use-site
tracking.

`kotlin-no-globals` enforces only one compiler-plugin rule:

- declarations that match the “global mutable state” rule must be annotated with `@RequiresGlobalState`

Once that annotation is present, Kotlin’s own `@RequiresOptIn` mechanism handles the rest:

- callers must use `@OptIn(RequiresGlobalState::class)`
- opt-in propagates through declarations in the normal Kotlin way

That split is the key architectural simplification in this repo.

## Configuration Flow

Configuration starts in Gradle and ends inside the FIR checker:

1. The Gradle plugin exposes:
   - `noGlobals.enabled`
   - `noGlobals.blacklistedTypes`
2. It converts those values into compiler-plugin options:
   - `enabled`
   - `blacklistedType`
3. The command-line processor parses those options into a `CompilerConfiguration`
4. The compiler plugin registrar turns that into `NoGlobalsConfiguration`
5. The FIR checkers use that configuration during analysis

The Gradle plugin is convenience and integration; the compiler plugin is the real source of truth for semantics.

## Why There Are Two Kinds Of Checks

The enforcement logic has two main checker paths:

### Property checker

Used for:

- global `var`
- blacklisted stored `val`
- anonymous object holders with mutable members
- enum-body mutable properties

This is the path that covers the obvious property-shaped global state.

### Regular-class checker

Used for:

- singleton objects whose own type is itself a blacklisted mutable carrier

Example:

```kotlin
object Users : MutableList<String> by mutableListOf()
```

Without a class-level checker, this shape would be invisible because there is no user-written
global property to report.

## Scope Classification

The checker classifies declarations into a small number of meaningful scopes:

- top level
- object singleton
- enum class
- enum entry

Ordinary class instance state and local declarations are excluded.

This classification is declaration-context based, not import-string or PSI-text based.

## Blacklist Model

The blacklist is declaration-type driven.

The checker matches:

- direct blacklisted types
- subtypes of blacklisted types

It does **not** currently inspect initializer types to catch disguised storage such as:

```kotlin
val users: List<String> = mutableListOf()
```

That tradeoff is explicit. The project prefers a smaller, more predictable rule over a broader
but less comprehensible one.

## Computed Properties

A pure computed `val` is treated differently from stored state.

Currently, a property is considered a pure computed `val` when it has:

- no initializer
- no delegate
- an explicit getter

That allows patterns like:

```kotlin
val users: MutableList<String>
    get() = mutableListOf()
```

while still rejecting:

```kotlin
val users: MutableList<String> by lazy { mutableListOf() }
```

## Composite Build Story

For local downstream development, there are really two separate resolution problems:

- resolving the Gradle plugin ID
- resolving the compiler-plugin and annotation artifacts

That is why local consumers need both:

```kotlin
pluginManagement {
    includeBuild("../kotlin-no-globals")
}

includeBuild("../kotlin-no-globals")
```

The first handles plugin resolution. The second handles dependency substitution.

This distinction is subtle and important enough that it is covered by functional tests.

## Native Coverage

The Gradle plugin functional suite includes native coverage, not just JVM compilation. That matters
because declaration-level opt-in and plugin classpath wiring have historically been the sort of
thing that can behave differently across targets.

## Design Principle

The repo consistently favors:

- explicit declarations over inference
- declared types over initializer spelunking
- predictable enforcement over ambitious hidden-state detection

If a future change makes the rule broader, it should justify the loss in predictability explicitly.
