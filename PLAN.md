# kotlin-no-globals Review Follow-up Checklist

## Goal

- [x] Keep the plugin behavior simple: any global `var` is bad.
- [x] Keep `lateinit` called out in diagnostics because it is useful context.
- [x] Treat `@Volatile` as non-special; a global `var` is already enough.
- [x] Reject only true global mutable state, not ordinary instance or local mutation.
- [x] Expand detection beyond plain global `var`s to catch selected mutable global `val` patterns.
- [x] Detect top-level/global anonymous object holders that expose mutable members.
- [x] Detect mutable state declared in enum class bodies and enum entry bodies.
- [x] Add a configurable mutable-type blacklist with sane defaults.

## Working Order

- [x] Add regression tests first.
- [x] Make the checker fail on the new tests.
- [x] Fix the implementation until the full suite passes.
- [x] Keep the resulting behavior documented and boring.

## Review Items

### `@Volatile` regex hack

- [x] Remove all `@Volatile`-specific detection.
- [x] Remove any diagnostic wording that treats `@Volatile` as a special category.
- [x] Keep one regression test proving a volatile global `var` is still rejected only because it is a global `var`.

### Nested class scoping inside `object` / `companion object`

- [x] Classify scope from the innermost containing regular class, not from any ancestor.
- [x] Treat only properties whose direct owning class is an `OBJECT` as singleton-global.
- [x] Ensure properties in a nested ordinary class inside an object remain instance properties and compile.
- [x] Add regression test: `object Outer { class Inner { var x = 0 } }` should compile.
- [x] Add regression test: `class Outer { companion object { class Inner { var x = 0 } } }` should compile.

### Containing-declaration ordering

- [x] Add a short comment near the helper explaining the assumed ordering of `CheckerContext.containingDeclarations`.
- [x] Avoid relying on ancestor scans where order matters.
- [x] Rework enum-entry scope classification to honor the nearest relevant containing declaration instead of combining unrelated `findClosest` queries.
- [x] Ensure anonymous objects or regular classes nested inside enum-entry bodies are not themselves misclassified as enum-entry state.

### FIR checker API safety

- [x] Stop reading `symbol.fir` from `CheckerContext.containingDeclarations`.
- [x] Rework the containing-class and local-scope helpers to use checker-context/symbol APIs that are safe outside CLI `BODY_RESOLVE`.
- [x] Add or keep a regression test pass around the affected scope-classification cases after the helper rewrite.

### Regression coverage for local/object boundaries

- [x] Add a focused set of missing tests instead of turning the suite into a taxonomy.
- [x] Add test: nested ordinary class inside object is ignored.
- [x] Add test: nested ordinary class inside companion object is ignored.
- [x] Add test: mutable property inside anonymous object expression is ignored.
- [x] Add test: nested object inside object still counts as singleton-global.
- [x] Add test: nested object inside companion object still counts as singleton-global.
- [x] Add test: top-level delegated `var` is rejected.
- [x] Replace the old documentation test with a failing regression: top-level `val holder = object { var x = 0 }` should be rejected.
- [x] Add regression test: enum entry body mutable property is rejected.
- [x] Add regression test: enum class body mutable property is rejected.
- [x] Add regression test: enum entry property holding an anonymous object with mutable members is rejected.
- [x] Add regression test: enum-entry anonymous-object holders flag the holder property, not mutable members inside the anonymous object.
- [x] Add regression test: a local class inside an enum-entry method remains local and compiles.
- [x] Add regression test: an anonymous object that only contains a local class inside a method does not false-positive.
- [x] Add regression test coverage for `data object` singleton globals.
- [x] Add regression test coverage for blacklisted delegated globals and custom-accessor globals.
- [x] Add explicit regression test: `const val` remains allowed.
- [x] Add explicit regression test: a local `val` holding an anonymous object with mutable members remains allowed.
- [x] Decide that standard Kotlin `@Suppress` should remain a supported escape hatch and add regression coverage for it.

### Expanded mutable-state scope

- [x] Detect top-level and singleton `val`s whose declared or resolved type is on a mutable-state blacklist.
- [x] Detect anonymous object holders by inspecting mutable members on a top-level or singleton-bound object expression.
- [x] Detect enum-body mutable properties as global mutable state even though they are instance members.
- [x] Include both enum class bodies and enum entry bodies.
- [x] Keep blacklist matching declaration-type-only; do not inspect initializer types for disguised globals such as `val users: List<String> = mutableListOf()`.
- [x] Do not add initializer-based blacklist regressions unless the rule scope changes intentionally.
- [x] Exempt pure computed `val`s with no stored backing state from the blacklisted-type rule.
- [x] Keep delegated or otherwise stored `val`s on the blacklisted-type path even when they are not `var`s.
- [x] Detect object singletons that are themselves blacklisted mutable carriers, such as `object Users : MutableList<String> by ...`.
- [x] Make the class-level `@RequiresGlobalState` target meaningful by allowing it to bless singleton mutable-carrier objects and require use-site opt-in.

### Configurable mutable-type blacklist

- [x] Introduce a compiler configuration entry for additional blacklisted mutable-state carrier types.
- [x] Expose the blacklist through the Gradle DSL alongside `enabled`.
- [x] Start with sane defaults for common mutable carriers.
  - Kotlin collection interfaces such as `kotlin.collections.MutableList`, `MutableSet`, and `MutableMap`
  - obvious JDK mutable carriers such as `java.util.concurrent.atomic.AtomicInteger`
- [x] Decide that defaults also match specific supertypes / subclasses when the resolved type inherits from a blacklisted type.
- [x] Expand the built-in blacklist to cover broader mutable carriers such as atomic arrays, Kotlin atomics, mutable flows, AtomicFU atomics, and string builders.
- [x] Expand the built-in blacklist further to cover concurrency/synchronization carriers such as JDK atomics/adders/locks and coroutine mutexes/channels.
- [x] Add tests proving:
  - default blacklisted types are rejected in top-level or singleton `val`s
  - non-blacklisted immutable-looking `val`s still compile
  - user-supplied blacklist entries extend the defaults rather than replacing them by accident
- [x] Validate or correctly parse nested blacklisted type names so `Outer.Inner` does not silently misconfigure the rule.
- [x] Add a regression test proving a subtype of a blacklisted mutable carrier is also rejected.
- [x] Validate malformed configured blacklist entries fail loudly instead of silently generating useless candidates.

### Gradle plugin DSL

- [x] Add a `noGlobals { enabled = ... }` extension.
- [x] Prove `enabled = false` disables checking by:
  - testing Gradle-side option wiring from the `noGlobals` extension
  - testing compiler-side behavior with `enabled=false`
- [x] Extend the Gradle DSL with blacklist configuration once the compiler option exists.
- [x] Decide whether the Gradle plugin should auto-add the marker annotation library dependency or remain explicit and documented.
- [x] Ensure the Gradle plugin does not add duplicate marker-library dependencies when overlapping Kotlin plugin hooks fire.

### Gradle functional coverage

- [x] Add a Gradle/TestKit smoke test that applies the published plugin ID in a real build and proves the compiler plugin actually runs.
- [x] Fold the local composite-build substitution path into that functional coverage once the composite-resolution gap is fixed.

### Local composite resolution

- [x] Confirm that `pluginManagement { includeBuild(...) }` alone only resolves the Gradle plugin ID; root `includeBuild(...)` is still required for dependency substitution in local functional tests.
- [x] Align the build wiring or documentation so the documented local composite setup is actually covered by tests.

### Test harness `pluginArtifact()` brittleness

- [x] Inspect the helper while touching tests.
- [x] Fix the concrete failure mode exposed during the TDD pass.
- [x] Resolve the runtime plugin jar deterministically instead of taking the first `.jar` in `build/libs`.
- [x] Improve the helper further so tests can run from additional entrypoints via an explicit plugin-jar override.

### CLI option multiplicity

- [x] Set `allowMultipleOccurrences` to `false` for `enabled`.

### `@RequiresGlobalState` target set

- [x] Tighten the declaration contract so only a property-level `@RequiresGlobalState` satisfies the checker.
- [x] Remove `PROPERTY_GETTER` because Kotlin opt-in markers cannot be used on getters.
- [x] Stop treating `@set:RequiresGlobalState` as sufficient to bless a global mutable declaration.
- [x] Decide whether `PROPERTY_SETTER` should be removed from the annotation target set once setter-only blessing no longer works.
- [x] Replace the current setter-only passing regression with coverage proving setter-only annotation is rejected.
- [x] Add regression test: `@RequiresGlobalState` on a mutable property inside an `object` / companion object compiles and still requires use-site opt-in.

## Concrete Edit List

### Tests

- [x] Update `CompilerIntegrationTest` to drop volatile-special wording.
- [x] Add nested-class-in-object regression tests.
- [x] Add anonymous-object-local regression test.
- [x] Add compiler integration coverage for `enabled=false`.
- [x] Add Gradle plugin tests for the `noGlobals.enabled` option wiring.
- [x] Add failing regressions for blacklisted mutable global `val`s.
- [x] Add failing regressions for top-level/global anonymous object holders with mutable members.
- [x] Add failing regressions for enum mutable state.
- [x] Add Gradle/plugin tests for blacklist option wiring.
- [x] Add a blacklist subtype regression.
- [x] Add a singleton-property annotation regression.
- [x] Add a singleton-object mutable-carrier regression.
- [x] Flip the computed-getter mutable-`val` regression to an allowed case.
- [x] Add an enum-entry anonymous-object-holder regression.
- [x] Add a regression proving setter-only annotation does not satisfy the checker.
- [x] Add a regression proving enum-entry nested anonymous-object members are not double-flagged.
- [x] Add a regression proving local classes inside anonymous objects or enum entries do not false-positive.
- [x] Add a Gradle/TestKit functional smoke test.

### Implementation

- [x] Simplify `GlobalMutableStateViolation` to remove `isVolatile`.
- [x] Delete the regex/source-text `@Volatile` detection path.
- [x] Replace ancestor `any { classKind == OBJECT }` logic with innermost-owner classification.
- [x] Add a clarifying comment around containing-declaration ordering.
- [x] Set CLI option multiplicity to false.
- [x] Add a Gradle extension for `noGlobals.enabled`.
- [x] Add mutable-type blacklist configuration plumbing from CLI to checker.
- [x] Teach the checker to flag top-level/singleton mutable global `val`s whose type matches the blacklist.
- [x] Teach the checker to flag top-level/singleton anonymous object holders with mutable members.
- [x] Teach the checker to flag enum-body mutable properties.
- [x] Teach blacklist matching to accept nested configured type names such as `Outer.Inner`.
- [x] Restrict declaration blessing to property annotations and remove the setter-only loophole in `hasRequiresGlobalStateMarker()`.
- [x] Tighten enum-entry and anonymous-object scope classification so only the nearest relevant container determines whether a declaration is global.
- [x] Narrow anonymous-object mutable-member detection so local classes nested under anonymous-object methods do not count as mutable state owned by the anonymous object itself.
- [x] Make compiler-plugin test jar resolution deterministic and ignore sources/javadoc artifacts.
- [x] Exempt pure computed `val`s from the blacklisted-type path while keeping delegated/stored `val`s checked.
- [x] Add a regular-class checker for object singletons that are themselves blacklisted mutable carriers.

## Verification

- [x] Run `./gradlew :compiler-plugin:test`.
- [x] Run `./gradlew :gradle-plugin:test :kotlin-no-globals:compileKotlinJvm`.
