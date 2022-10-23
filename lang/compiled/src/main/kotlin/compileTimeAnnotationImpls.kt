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
    private val impl: CtAnnotation,
) : ConditionAnnotationLangModel {
    override val target: Type = impl.getType("value")
    override val condition: String = impl.getString("condition")
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotation, CtConditionAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotation) = createCached(impl, ::CtConditionAnnotationImpl)
    }
}

internal class CtAnyConditionAnnotationImpl private constructor(
    private val impl: CtAnnotation,
) : AnyConditionAnnotationLangModel {
    override val conditions: Sequence<ConditionAnnotationLangModel> =
        impl.getAnnotations("value").map { CtConditionAnnotationImpl(it) }
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotation, CtAnyConditionAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotation) = createCached(impl, ::CtAnyConditionAnnotationImpl)
    }
}

internal class CtConditionalAnnotationImpl private constructor(
    private val impl: CtAnnotation,
) : ConditionalAnnotationLangModel {
    override val featureTypes: Sequence<Type> = impl.getTypes("value")
    override val onlyIn: Sequence<Type> = impl.getTypes("onlyIn")
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotation, CtConditionalAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotation) = createCached(impl, ::CtConditionalAnnotationImpl)
    }
}

internal class CtComponentFlavorAnnotationImpl private constructor(
    private val impl: CtAnnotation,
) : ComponentFlavorAnnotationLangModel {
    override val dimension: Type = impl.getType("dimension")
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotation, CtComponentFlavorAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotation) = createCached(impl, ::CtComponentFlavorAnnotationImpl)
    }
}

internal class CtProvidesAnnotationImpl private constructor(
    private val impl: CtAnnotation,
) : ProvidesAnnotationLangModel {
    override val conditionals: Sequence<ConditionalAnnotationLangModel> =
        impl.getAnnotations("value").map { CtConditionalAnnotationImpl(it) }
    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotation, CtProvidesAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotation) = createCached(impl, ::CtProvidesAnnotationImpl)
    }
}

internal class CtIntoCollectionAnnotationImpl private constructor(
    private val impl: CtAnnotation,
) : IntoCollectionAnnotationLangModel {
    override val flatten: Boolean
        get() = impl.getBoolean("flatten")

    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotation, CtIntoCollectionAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotation) = createCached(impl, ::CtIntoCollectionAnnotationImpl)
    }
}

internal class CtAssistedAnnotationImpl private constructor(
    private val impl: CtAnnotation,
) : AssistedAnnotationLangModel {
    override val value: String
        get() = impl.getString("value")

    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<CtAnnotation, CtAssistedAnnotationImpl>() {
        operator fun invoke(impl: CtAnnotation) = createCached(impl, ::CtAssistedAnnotationImpl)
    }
}