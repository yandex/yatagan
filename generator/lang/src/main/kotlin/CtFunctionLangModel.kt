package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.core.lang.IntoListAnnotationLangModel
import com.yandex.daggerlite.core.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.core.lang.hasType
import com.yandex.daggerlite.lang.common.FunctionLangModelBase
import kotlin.LazyThreadSafetyMode.PUBLICATION

abstract class CtFunctionLangModel : FunctionLangModelBase() {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    final override val providesAnnotationIfPresent: ProvidesAnnotationLangModel? by lazy(PUBLICATION) {
        annotations.find { it.hasType<Provides>() }?.let { CtProvidesAnnotationImpl(it) }
    }

    final override val intoListAnnotationIfPresent: IntoListAnnotationLangModel? by lazy(PUBLICATION) {
        annotations.find { it.hasType<IntoList>() }?.let { CtIntoListAnnotationImpl(it) }
    }
}