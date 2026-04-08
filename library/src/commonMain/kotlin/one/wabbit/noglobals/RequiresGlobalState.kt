// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.noglobals

/**
 * Marks a declaration as intentionally relying on global mutable state.
 *
 * `kotlin-no-globals` uses this annotation as the single explicit escape hatch for declarations the
 * compiler plugin considers global mutable state. Typical examples include:
 *
 * - top-level `var`
 * - mutable properties declared inside `object` singletons
 * - top-level or singleton stored `val`s whose declared type is on the mutable-type blacklist
 * - singleton objects whose own type is a blacklisted mutable carrier
 *
 * Because this annotation is also a Kotlin [RequiresOptIn] marker, use-site acknowledgement comes
 * for free: callers must opt in explicitly with `@OptIn(RequiresGlobalState::class)`.
 *
 * Typical property usage:
 *
 * ```kotlin
 * @RequiresGlobalState
 * var counter: Int = 0
 * ```
 *
 * Typical singleton-object usage:
 *
 * ```kotlin
 * @RequiresGlobalState
 * object Users : MutableList<String> by mutableListOf()
 * ```
 *
 * This annotation targets [AnnotationTarget.PROPERTY], [AnnotationTarget.CLASS], and
 * [AnnotationTarget.FUNCTION]:
 *
 * - use `PROPERTY` for declarations directly reported by the checker
 * - use `CLASS` for singleton mutable-carrier objects
 * - use `FUNCTION` when you want opt-in to propagate through helper APIs intentionally
 *
 * The plugin deliberately does not support getter-only or setter-only blessing. If the mutable
 * state declaration is accepted, the declaration itself should be visibly marked.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This declaration relies on global mutable state.",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
@Retention(AnnotationRetention.BINARY)
public annotation class RequiresGlobalState
