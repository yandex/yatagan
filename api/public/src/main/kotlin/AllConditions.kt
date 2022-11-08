package com.yandex.daggerlite

/**
 * Container annotation for [Condition].
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class AllConditions(
    vararg val value: Condition,
)