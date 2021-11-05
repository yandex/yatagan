package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize

internal open class CtAnnotationBase(
    private val impl: CtAnnotationLangModel,
) {
    override fun toString() = impl.toString()
    final override fun hashCode() = impl.hashCode()
    final override fun equals(other: Any?): Boolean {
        return this === other || (other is CtAnnotationBase && impl == other.impl)
    }
}

internal class CtConditionAnnotationImpl(
    impl: CtAnnotationLangModel,
) : CtAnnotationBase(impl), ConditionAnnotationLangModel {
    override val target: TypeLangModel = impl.getType("value")
    override val condition: String = impl.getString("condition")
}

internal class CtAnyConditionAnnotationImpl(
    impl: CtAnnotationLangModel,
) : CtAnnotationBase(impl), AnyConditionAnnotationLangModel {
    override val conditions: Sequence<ConditionAnnotationLangModel> =
        impl.getAnnotations("value").map(::CtConditionAnnotationImpl).memoize()
}

internal class CtConditionalAnnotationImpl(
    impl: CtAnnotationLangModel,
) : CtAnnotationBase(impl), ConditionalAnnotationLangModel {
    override val featureTypes: Sequence<TypeLangModel> = impl.getTypes("value")
    override val onlyIn: Sequence<TypeLangModel> = impl.getTypes("onlyIn")
}

internal class ComponentFlavorAnnotationImpl(
    impl: CtAnnotationLangModel,
) : CtAnnotationBase(impl), ComponentFlavorAnnotationLangModel {
    override val dimension: TypeLangModel = impl.getType("dimension")
}