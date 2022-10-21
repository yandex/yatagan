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

/**
 * [com.yandex.daggerlite.Assisted] annotation model.
 */
interface AssistedAnnotationLangModel {
    val value: String
}

/**
 * Models [com.yandex.daggerlite.Component] annotation.
 */
interface ComponentAnnotationLangModel {
    val isRoot: Boolean
    val modules: Sequence<TypeLangModel>
    val dependencies: Sequence<TypeLangModel>
    val variant: Sequence<TypeLangModel>
    val multiThreadAccess: Boolean
}

/**
 * Models [com.yandex.daggerlite.IntoList]/[com.yandex.daggerlite.IntoSet] annotations.
 */
interface IntoCollectionAnnotationLangModel {
    val flatten: Boolean
}

/**
 * Models [com.yandex.daggerlite.Module] annotation.
 */
interface ModuleAnnotationLangModel {
    val includes: Sequence<TypeLangModel>
    val subcomponents: Sequence<TypeLangModel>
}

/**
 * Models [com.yandex.daggerlite.Provides] annotation.
 */
interface ProvidesAnnotationLangModel {
    val conditionals: Sequence<ConditionalAnnotationLangModel>
}