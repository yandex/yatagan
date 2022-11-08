package com.yandex.daggerlite

/**
 * Logical `||` operator for [Conditions][Condition].
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
@JvmRepeatable(AnyConditions::class)
annotation class AnyCondition(
    vararg val value: Condition,
)