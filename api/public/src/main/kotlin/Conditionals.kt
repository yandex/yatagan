package com.yandex.daggerlite

/**
 * Container annotation for [Conditional].
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Conditionals(
    vararg val value: Conditional,
)