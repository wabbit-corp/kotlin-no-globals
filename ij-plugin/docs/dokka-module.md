# Module kotlin-no-globals-ij-plugin

IntelliJ IDEA helper plugin for `one.wabbit.no-globals` projects.

This module does not implement separate IDE-native no-globals inspections. Its current job is to
help the bundled Kotlin IDE plugin load the external `kotlin-no-globals` compiler plugin registrar
for trusted imported projects that already apply `one.wabbit.no-globals` in Gradle.

## Current Scope

- detect whether imported compiler arguments reference the `kotlin-no-globals` compiler plugin
- detect `one.wabbit.no-globals` usage directly from Gradle build files and version catalogs
- request Gradle reimport when the Gradle plugin is visible but the compiler plugin classpath is not yet imported
- surface IDE-side enablement controls for trusted projects
- coordinate refresh and rescan behavior when project configuration changes

## Important Boundary

The IntelliJ platform currently exposes only coarse-grained support for external K2 compiler
plugins in this integration path. Enabling support for `kotlin-no-globals` allows the IDE session
to load non-bundled K2 compiler plugins for the current trusted project; it is not a dedicated
per-plugin sandbox.

## Relationship To The Other Modules

Most end users interact with:

- `one.wabbit:kotlin-no-globals` for the source annotation
- `one.wabbit:kotlin-no-globals-gradle-plugin` for build integration
- Kotlin-line-specific `one.wabbit:kotlin-no-globals-plugin` artifacts resolved by the Gradle plugin

This IDEA plugin is a companion integration layer for local IDE behavior, not a replacement for the
compiler plugin itself.
