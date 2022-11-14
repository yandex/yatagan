package com.yandex.yatagan

/**
 * Container annotation for [AnyCondition].
 *
 * @see Condition
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class AnyConditions(
    vararg val value: AnyCondition,
)