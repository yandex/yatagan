package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.Provides
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

    final override val intoListAnnotationLangModel: IntoListAnnotationLangModel? by lazy(NONE) {
        annotations.find { it.hasType<IntoList>() }?.let { CtIntoListAnnotationImpl(it) }
    }

    override fun toString() = "function $name(${parameters.toList()}) -> $returnType"
}