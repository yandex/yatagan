package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.lang.IntoCollectionAnnotationLangModel
import com.yandex.daggerlite.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.lang.Type

internal class CtConditionAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : ConditionAnnotationLangModel {
    override val target: Type = impl.getType("value")
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
    override val featureTypes: Sequence<Type> = impl.getTypes("value")
    override val onlyIn: Sequence<Type> = impl.getTypes("onlyIn")
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtConditionalAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtConditionalAnnotationImpl)
    }
}

internal class CtComponentFlavorAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : ComponentFlavorAnnotationLangModel {
    override val dimension: Type = impl.getType("dimension")
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

internal class CtIntoCollectionAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : IntoCollectionAnnotationLangModel {
    override val flatten: Boolean
        get() = impl.getBoolean("flatten")

    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtIntoCollectionAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtIntoCollectionAnnotationImpl)
    }
}

internal class CtAssistedAnnotationImpl private constructor(
    private val impl: CtAnnotationLangModel,
) : AssistedAnnotationLangModel {
    override val value: String
        get() = impl.getString("value")

    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotationLangModel, CtAssistedAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotationLangModel) = createCached(impl, ::CtAssistedAnnotationImpl)
    }
}