# Module kotlin-no-globals

`kotlin-no-globals` is the published annotation module for the global-mutable-state enforcement
stack.

This module exposes the `@RequiresGlobalState` marker used by the compiler plugin and by callers
that need to opt in explicitly to APIs built on global mutable state.

Use this module directly when:

- you want the annotation on your classpath without the Gradle plugin
- you are wiring the compiler plugin manually
- you want to annotate APIs in a library that may be consumed from builds not applying the Gradle plugin
