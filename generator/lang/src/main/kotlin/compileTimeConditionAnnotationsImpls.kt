package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize

internal open class CompileTimeAnnotationBase(
    private val impl: CompileTimeAnnotationLangModel,
) {
    override fun toString() = impl.toString()
    final override fun hashCode() = impl.hashCode()
    final override fun equals(other: Any?): Boolean {
        return this === other || (other is CompileTimeAnnotationBase && impl == other.impl)
    }
}

internal class CompileTimeConditionAnnotationImpl(
    impl: CompileTimeAnnotationLangModel,
) : CompileTimeAnnotationBase(impl), ConditionAnnotationLangModel {
    override val target: TypeLangModel = impl.getType("value")
    override val condition: String = impl.getString("condition")
}

internal class CompileTimeAnyConditionAnnotationImpl(
    impl: CompileTimeAnnotationLangModel,
) : CompileTimeAnnotationBase(impl), AnyConditionAnnotationLangModel {
    override val conditions: Sequence<ConditionAnnotationLangModel> =
        impl.getAnnotations("value").map(::CompileTimeConditionAnnotationImpl).memoize()
}

internal class CompileTimeConditionalAnnotationImpl(
    impl: CompileTimeAnnotationLangModel,
) : CompileTimeAnnotationBase(impl), ConditionalAnnotationLangModel {
    override val featureTypes: Sequence<TypeLangModel> = impl.getTypes("value")
    override val onlyIn: Sequence<TypeLangModel> = impl.getTypes("onlyIn")
}

internal class ComponentFlavorAnnotationImpl(
    impl: CompileTimeAnnotationLangModel,
) : CompileTimeAnnotationBase(impl), ComponentFlavorAnnotationLangModel {
    override val dimension: TypeLangModel = impl.getType("dimension")
}