package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.DeclareList
import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.core.lang.DeclareListAnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.IntoListAnnotationLangModel
import com.yandex.daggerlite.core.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.core.lang.hasType
import kotlin.LazyThreadSafetyMode.NONE

abstract class CtFunctionLangModel : FunctionLangModel {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    final override val providesAnnotationIfPresent: ProvidesAnnotationLangModel? by lazy(NONE) {
        annotations.find { it.hasType<Provides>() }?.let { CtProvidesAnnotationImpl(it) }
    }

    final override val intoListAnnotationIfPresent: IntoListAnnotationLangModel? by lazy(NONE) {
        annotations.find { it.hasType<IntoList>() }?.let { CtIntoListAnnotationImpl(it) }
    }

    final override val declareListAnnotationIfPresent: DeclareListAnnotationLangModel? by lazy(NONE) {
        annotations.find { it.hasType<DeclareList>() }?.let { CtDeclareListAnnotationImpl(it) }
    }

    override fun toString() = buildString {
        append(owner.qualifiedName)
        append("::")
        append(name).append('(')
        parameters.joinTo(this)
        append("): ")
        append(returnType)
    }
}