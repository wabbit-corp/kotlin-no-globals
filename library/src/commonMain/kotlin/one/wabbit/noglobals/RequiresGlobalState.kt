package one.wabbit.noglobals

/**
 * Marks declarations that rely on global mutable state.
 *
 * The compiler plugin requires this annotation on detected global mutable properties such as
 * top-level `var`s and `var`s declared inside object singletons. Because this marker is also a
 * Kotlin opt-in requirement, every use site must acknowledge it with `@OptIn`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This declaration relies on global mutable state.",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_SETTER,
)
@Retention(AnnotationRetention.BINARY)
public annotation class RequiresGlobalState
