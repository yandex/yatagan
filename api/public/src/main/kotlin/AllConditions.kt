package com.yandex.yatagan

/**
 * Container annotation for [Condition].
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class AllConditions(
    vararg val value: Condition,
)