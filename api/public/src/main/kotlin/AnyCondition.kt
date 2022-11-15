package com.yandex.yatagan

/**
 * Logical `||` operator for [Conditions][Condition].
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
@JvmRepeatable(AnyConditions::class)
public annotation class AnyCondition(
    vararg val value: Condition,
)