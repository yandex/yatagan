package com.yandex.daggerlite.core.lang

/**
 * An interface for a condition literal or an `or` expression.
 * `and` expression is implicit "root".
 */
sealed interface ConditionLangModel

/**
 * Represents [com.yandex.daggerlite.Condition] annotation.
 */
interface ConditionAnnotationLangModel : ConditionLangModel {
    val target: TypeLangModel
    val condition: String
}

/**
 * Represents [com.yandex.daggerlite.AnyCondition] annotation.
 */
interface AnyConditionAnnotationLangModel : ConditionLangModel {
    val conditions: Sequence<ConditionAnnotationLangModel>
}

/**
 * Represents [com.yandex.daggerlite.Conditional] annotation.
 */
interface ConditionalAnnotationLangModel {
    val featureTypes: Sequence<TypeLangModel>
    val onlyIn: Sequence<TypeLangModel>
}

/**
 * Represents [com.yandex.daggerlite.ComponentFlavor] annotation.
 */
interface ComponentFlavorAnnotationLangModel {
    val dimension: TypeLangModel
}