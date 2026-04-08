# kotlin-no-globals Compiler Plugin

This module contains the K2 FIR compiler plugin implementation for `kotlin-no-globals`.

It is responsible for:

- detecting global `var`
- detecting selected stored mutable `val` patterns
- detecting singleton objects that are themselves mutable carriers
- reporting diagnostics that require `@RequiresGlobalState`

## Artifact Coordinate

The compiler plugin artifact is Kotlin-line-specific:

```text
one.wabbit:kotlin-no-globals-plugin:<baseVersion>-kotlin-<kotlinVersion>
```

Example shape:

```text
one.wabbit:kotlin-no-globals-plugin:0.0.1-kotlin-2.3.10
```

Use the variant whose Kotlin suffix matches the compiler you are running.

## Direct Compiler Usage

If you are not using the Gradle plugin, pass the compiler plugin explicitly:

```text
-Xplugin=/path/to/kotlin-no-globals-plugin.jar
-P plugin:one.wabbit.no-globals:enabled=true|false
-P plugin:one.wabbit.no-globals:blacklistedType=com.example.MutableBox
```

If your source code uses `@RequiresGlobalState`, the annotation library still needs to be present
on the compilation classpath.

## Compiler Options

Supported options:

- `enabled`
  - `true` or `false`
  - controls whether the plugin registers its FIR checkers
- `blacklistedType`
  - repeatable
  - extends the built-in mutable-type blacklist

Malformed blacklist values fail fast during option parsing.

## Implementation Shape

The compiler plugin currently uses:

- a property checker for property-shaped global mutable state
- a regular-class checker for singleton objects that are themselves mutable carriers

The enforcement model is intentionally simple:

- find a declaration that matches the rule
- require `@RequiresGlobalState` on that declaration
- let Kotlin’s standard opt-in machinery enforce use-site acknowledgement

## Scope

This compiler plugin is:

- K2 only
- FIR based
- declaration-shape and declared-type driven by design

It does not currently attempt full hidden-mutability inference.

For the exact semantics, see [../docs/rules.md](../docs/rules.md).
