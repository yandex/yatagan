package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.IntoListAnnotationLangModel
import com.yandex.daggerlite.core.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

internal class CtConditionAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : ConditionAnnotationLangModel {
    override val target: TypeLangModel = impl.getType("value")
    override val condition: String = impl.getString("condition")
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtConditionAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtConditionAnnotationImpl)
    }
}

internal class CtAnyConditionAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : AnyConditionAnnotationLangModel {
    override val conditions: Sequence<ConditionAnnotationLangModel> =
        impl.getAnnotations("value").map { CtConditionAnnotationImpl(it) }
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtAnyConditionAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtAnyConditionAnnotationImpl)
    }
}

internal class CtConditionalAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : ConditionalAnnotationLangModel {
    override val featureTypes: Sequence<TypeLangModel> = impl.getTypes("value")
    override val onlyIn: Sequence<TypeLangModel> = impl.getTypes("onlyIn")
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtConditionalAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtConditionalAnnotationImpl)
    }
}

internal class CtComponentFlavorAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : ComponentFlavorAnnotationLangModel {
    override val dimension: TypeLangModel = impl.getType("dimension")
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtComponentFlavorAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtComponentFlavorAnnotationImpl)
    }
}

internal class CtProvidesAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : ProvidesAnnotationLangModel {
    override val conditionals: Sequence<ConditionalAnnotationLangModel> =
        impl.getAnnotations("value").map { CtConditionalAnnotationImpl(it) }
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtProvidesAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtProvidesAnnotationImpl)
    }
}

internal class CtIntoListAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : IntoListAnnotationLangModel {
    override val flatten: Boolean
        get() = impl.getBoolean("flatten")

    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtIntoListAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtIntoListAnnotationImpl)
    }
}
