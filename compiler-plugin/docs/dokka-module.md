# Module kotlin-no-globals-plugin

This module contains the K2 FIR compiler plugin implementation for `kotlin-no-globals`.

It enforces the declaration-side rule:

- if a declaration matches the plugin's notion of global mutable state, it must be annotated with
  `@RequiresGlobalState`

Once that annotation is present, Kotlin's standard opt-in mechanism handles use-site acknowledgement.

The compiler plugin artifact is published per Kotlin compiler line using the form:

- `one.wabbit:kotlin-no-globals-plugin:<baseVersion>-kotlin-<kotlinVersion>`
