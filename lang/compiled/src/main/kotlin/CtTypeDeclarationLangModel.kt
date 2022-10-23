@file:OptIn(ConditionsApi::class, VariantApi::class)

package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.AllConditions
import com.yandex.daggerlite.AnyCondition
import com.yandex.daggerlite.AnyConditions
import com.yandex.daggerlite.AssistedFactory
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.ComponentVariantDimension
import com.yandex.daggerlite.Condition
import com.yandex.daggerlite.Conditional
import com.yandex.daggerlite.Conditionals
import com.yandex.daggerlite.ConditionsApi
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.VariantApi
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.common.TypeDeclarationLangModelBase

/**
 * [TypeDeclarationLangModel] specialized for compile time implementations.
 */
abstract class CtTypeDeclarationLangModel : TypeDeclarationLangModelBase() {
    abstract override val annotations: Sequence<CtAnnotation>

    override fun <T : BuiltinAnnotation.OnClass> getAnnotation(
        which: BuiltinAnnotation.Target.OnClass<T>
    ): T? {
        val value: BuiltinAnnotation.OnClass? = when (which) {
            BuiltinAnnotation.AssistedFactory -> (which as BuiltinAnnotation.AssistedFactory).takeIf {
                annotations.any { it.hasType<AssistedFactory>() }
            }
            BuiltinAnnotation.Module ->
                annotations.find { it.hasType<Module>() }?.let(::CtModuleAnnotationImpl)
            BuiltinAnnotation.Component ->
                annotations.find { it.hasType<Component>() }?.let(::CtComponentAnnotationImpl)
            BuiltinAnnotation.ComponentFlavor ->
                annotations.find { it.hasType<ComponentFlavor>() }?.let { CtComponentFlavorAnnotationImpl(it) }
            BuiltinAnnotation.ComponentVariantDimension -> (which as BuiltinAnnotation.ComponentVariantDimension).takeIf {
                annotations.any { it.hasType<ComponentVariantDimension>() }
            }
            BuiltinAnnotation.Component.Builder -> (which as BuiltinAnnotation.Component.Builder).takeIf {
                annotations.any { it.hasType<Component.Builder>() }
            }
        }
        return which.modelClass.cast(value)
    }

    override fun <T : BuiltinAnnotation.OnClassRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnClassRepeatable<T>
    ): List<T> {
        return when (which) {
            BuiltinAnnotation.ConditionFamily -> buildList {
                for (annotation in annotations) {
                    when {
                        annotation.hasType<Condition>() ->
                            add(which.modelClass.cast(CtConditionAnnotationImpl(annotation)))
                        annotation.hasType<AnyCondition>() ->
                            add(which.modelClass.cast(CtAnyConditionAnnotationImpl(annotation)))
                        annotation.hasType<AllConditions>() -> for (contained in annotation.getAnnotations("value"))
                            add(which.modelClass.cast(CtConditionAnnotationImpl(contained)))
                        annotation.hasType<AnyConditions>() -> for (contained in annotation.getAnnotations("value"))
                            add(which.modelClass.cast(CtAnyConditionAnnotationImpl(contained)))
                    }
                }
            }
            BuiltinAnnotation.Conditional -> buildList {
                for (annotation in annotations) {
                    when {
                        annotation.hasType<Conditional>() ->
                            add(which.modelClass.cast(CtConditionalAnnotationImpl(annotation)))
                        annotation.hasType<Conditionals>() -> for (contained in annotation.getAnnotations("value"))
                            add(which.modelClass.cast(CtConditionalAnnotationImpl(contained)))
                    }
                }
            }
        }
    }
}
