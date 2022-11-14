package com.yandex.yatagan

/**
 * Used in [Component.Builder] declaration to annotate setters or factory arguments.
 * The types (may be qualified) are then accessible in the graph.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class BindsInstance(
)