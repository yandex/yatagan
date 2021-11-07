package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.core.lang.hasType
import kotlin.LazyThreadSafetyMode.NONE

abstract class CtFunctionLangModel : FunctionLangModel {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    override val providesAnnotationIfPresent: ProvidesAnnotationLangModel? by lazy(NONE) {
        annotations.find { it.hasType<Provides>() }?.let { CtProvidesAnnotationImpl(it) }
    }
}