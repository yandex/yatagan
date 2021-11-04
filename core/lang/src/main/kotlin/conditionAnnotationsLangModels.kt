package com.yandex.daggerlite.core.lang

sealed interface ConditionLangModel

interface ConditionAnnotationLangModel : ConditionLangModel {
    val target: TypeLangModel
    val condition: String

    override fun hashCode(): Int
    override fun equals(other: Any?): Boolean
}

interface AnyConditionAnnotationLangModel : ConditionLangModel {
    val conditions: Sequence<ConditionAnnotationLangModel>
}

interface ConditionalAnnotationLangModel {
    val featureTypes: Sequence<TypeLangModel>
    val onlyIn: Sequence<TypeLangModel>
}

interface ComponentFlavorAnnotationLangModel {
    val dimension: TypeLangModel
}