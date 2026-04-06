# Module kotlin-no-globals-gradle-plugin

This module provides Gradle integration for `kotlin-no-globals`.

Its responsibilities are:

- exposing the `noGlobals {}` DSL
- adding compiler-plugin options to Kotlin compilations
- resolving the Kotlin-line-matched compiler-plugin artifact
- automatically adding the `one.wabbit:kotlin-no-globals` annotation dependency for supported Kotlin targets

Most users of `kotlin-no-globals` should start here rather than wiring compiler-plugin artifacts by hand.
