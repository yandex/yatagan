package com.yandex.daggerlite

/**
 * Container annotation for [AnyCondition].
 *
 * @see Condition
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class AnyConditions(
    vararg val value: AnyCondition,
)