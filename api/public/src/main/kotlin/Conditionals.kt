package com.yandex.yatagan

/**
 * Container annotation for [Conditional].
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class Conditionals(
    vararg val value: Conditional,
)