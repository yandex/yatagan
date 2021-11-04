package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.AllConditions
import com.yandex.daggerlite.AnyCondition
import com.yandex.daggerlite.AnyConditions
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.Condition
import com.yandex.daggerlite.Conditional
import com.yandex.daggerlite.Conditionals
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.core.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.hasType
import com.yandex.daggerlite.core.lang.memoize
import kotlin.LazyThreadSafetyMode.NONE

/**
 * [TypeDeclarationLangModel] specialized for compile time implementations.
 */
abstract class CompileTimeTypeDeclarationLangModel : TypeDeclarationLangModel {
    abstract override val annotations: Sequence<CompileTimeAnnotationLangModel>

    override val componentAnnotationIfPresent: ComponentAnnotationLangModel? by lazy(NONE) {
        annotations.find { it.hasType<Component>() }?.let(::CompileTimeComponentAnnotationImpl)
    }
    override val moduleAnnotationIfPresent: ModuleAnnotationLangModel? by lazy(NONE) {
        annotations.find { it.hasType<Module>() }?.let(::CompileTimeModuleAnnotationImpl)
    }
    override val conditions: Sequence<ConditionLangModel> = sequence {
        for (annotation in annotations) {
            when {
                annotation.hasType<Condition>() -> yield(CompileTimeConditionAnnotationImpl(annotation))
                annotation.hasType<AnyCondition>() -> yield(CompileTimeAnyConditionAnnotationImpl(annotation))
                annotation.hasType<AllConditions>() -> for (contained in annotation.getAnnotations("value"))
                    yield(CompileTimeConditionAnnotationImpl(contained))
                annotation.hasType<AnyConditions>() -> for (contained in annotation.getAnnotations("value"))
                    yield(CompileTimeAnyConditionAnnotationImpl(contained))
            }
        }
    }.memoize()

    override val conditionals: Sequence<ConditionalAnnotationLangModel> = sequence {
        for (annotation in annotations) {
            when {
                annotation.hasType<Conditional>() -> yield(CompileTimeConditionalAnnotationImpl(annotation))
                annotation.hasType<Conditionals>() -> for (contained in annotation.getAnnotations("value"))
                    yield(CompileTimeConditionalAnnotationImpl(contained))
            }
        }
    }.memoize()

    override val componentFlavorIfPresent: ComponentFlavorAnnotationLangModel? by lazy(NONE) {
        annotations.find { it.hasType<ComponentFlavor>() }?.let(::ComponentFlavorAnnotationImpl)
    }
}
