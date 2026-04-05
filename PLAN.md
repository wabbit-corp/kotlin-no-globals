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
- [ ] Consider adding tests for `data object` if this area expands.
- [ ] Consider adding tests for delegated globals with custom accessors if this area expands.

### Expanded mutable-state scope

- [x] Detect top-level and singleton `val`s whose declared or resolved type is on a mutable-state blacklist.
- [x] Detect anonymous object holders by inspecting mutable members on a top-level or singleton-bound object expression.
- [x] Detect enum-body mutable properties as global mutable state even though they are instance members.
- [x] Include both enum class bodies and enum entry bodies.

### Configurable mutable-type blacklist

- [x] Introduce a compiler configuration entry for additional blacklisted mutable-state carrier types.
- [x] Expose the blacklist through the Gradle DSL alongside `enabled`.
- [x] Start with sane defaults for common mutable carriers.
  - Kotlin collection interfaces such as `kotlin.collections.MutableList`, `MutableSet`, and `MutableMap`
  - obvious JDK mutable carriers such as `java.util.concurrent.atomic.AtomicInteger`
- [x] Decide that defaults also match specific supertypes / subclasses when the resolved type inherits from a blacklisted type.
- [x] Add tests proving:
  - default blacklisted types are rejected in top-level or singleton `val`s
  - non-blacklisted immutable-looking `val`s still compile
  - user-supplied blacklist entries extend the defaults rather than replacing them by accident

### Gradle plugin DSL

- [x] Add a `noGlobals { enabled = ... }` extension.
- [x] Prove `enabled = false` disables checking by:
  - testing Gradle-side option wiring from the `noGlobals` extension
  - testing compiler-side behavior with `enabled=false`
- [x] Extend the Gradle DSL with blacklist configuration once the compiler option exists.

### Local composite resolution

- [ ] Investigate why local composite builds do not currently substitute `one.wabbit:kotlin-no-globals-plugin` automatically in Gradle functional tests.
- [ ] Align the build wiring or documentation so the documented local composite setup is actually covered by tests.

### Test harness `pluginArtifact()` brittleness

- [x] Inspect the helper while touching tests.
- [x] Fix the concrete failure mode exposed during the TDD pass.
- [ ] Resolve the runtime plugin jar deterministically instead of taking the first `.jar` in `build/libs`.
- [ ] Improve the helper further if tests need to run from additional entrypoints later.

### CLI option multiplicity

- [x] Set `allowMultipleOccurrences` to `false` for `enabled`.

### `@RequiresGlobalState` target set

- [x] Align the annotation targets with what the checker actually honors.
- [x] Decide the accessor contract:
  - remove `PROPERTY_GETTER` because Kotlin opt-in markers cannot be used on getters
  - keep `PROPERTY_SETTER` and honor `@set:RequiresGlobalState`
- [x] Add regression tests for the chosen accessor contract.
- [ ] Add `FILE` to the annotation target set.
Reason: deferred; that would introduce new semantics not implemented by the checker yet.

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

## Verification

- [x] Run `./gradlew :compiler-plugin:test`.
- [x] Run `./gradlew :gradle-plugin:test :kotlin-no-globals:compileKotlinJvm`.
