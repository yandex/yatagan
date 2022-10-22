@file:OptIn(ConditionsApi::class, VariantApi::class)

package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.AllConditions
import com.yandex.daggerlite.AnyCondition
import com.yandex.daggerlite.AnyConditions
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.Condition
import com.yandex.daggerlite.Conditional
import com.yandex.daggerlite.Conditionals
import com.yandex.daggerlite.ConditionsApi
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.VariantApi
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.lang.ConditionLangModel
import com.yandex.daggerlite.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.common.TypeDeclarationLangModelBase
import com.yandex.daggerlite.lang.hasType
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * [TypeDeclarationLangModel] specialized for compile time implementations.
 */
abstract class CtTypeDeclarationLangModel : TypeDeclarationLangModelBase() {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    override val componentAnnotationIfPresent: ComponentAnnotationLangModel? by lazy(PUBLICATION) {
        annotations.find { it.hasType<Component>() }?.let(::CtComponentAnnotationImpl)
    }
    override val moduleAnnotationIfPresent: ModuleAnnotationLangModel? by lazy(PUBLICATION) {
        annotations.find { it.hasType<Module>() }?.let(::CtModuleAnnotationImpl)
    }
    override val conditions: Sequence<ConditionLangModel> = sequence {
        for (annotation in annotations) {
            when {
                annotation.hasType<Condition>() -> yield(CtConditionAnnotationImpl(annotation))
                annotation.hasType<AnyCondition>() -> yield(CtAnyConditionAnnotationImpl(annotation))
                annotation.hasType<AllConditions>() -> for (contained in annotation.getAnnotations("value"))
                    yield(CtConditionAnnotationImpl(contained))
                annotation.hasType<AnyConditions>() -> for (contained in annotation.getAnnotations("value"))
                    yield(CtAnyConditionAnnotationImpl(contained))
            }
        }
    }.memoize()

    override val conditionals: Sequence<ConditionalAnnotationLangModel> = sequence {
        for (annotation in annotations) {
            when {
                annotation.hasType<Conditional>() -> yield(CtConditionalAnnotationImpl(annotation))
                annotation.hasType<Conditionals>() -> for (contained in annotation.getAnnotations("value"))
                    yield(CtConditionalAnnotationImpl(contained))
            }
        }
    }.memoize()

    override val componentFlavorIfPresent: ComponentFlavorAnnotationLangModel? by lazy(PUBLICATION) {
        annotations.find { it.hasType<ComponentFlavor>() }?.let { CtComponentFlavorAnnotationImpl(it) }
    }
}
