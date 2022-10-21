package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.IntoSet
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.core.lang.IntoCollectionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.core.lang.hasType
import com.yandex.daggerlite.lang.common.FunctionLangModelBase

abstract class CtFunctionLangModel : FunctionLangModelBase() {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    final override val providesAnnotationIfPresent: ProvidesAnnotationLangModel?
        get() = annotations.find { it.hasType<Provides>() }?.let { CtProvidesAnnotationImpl(it) }

    final override val intoListAnnotationIfPresent: IntoCollectionAnnotationLangModel?
        get() = annotations.find { it.hasType<IntoList>() }?.let { CtIntoCollectionAnnotationImpl(it) }

    final override val intoSetAnnotationIfPresent: IntoCollectionAnnotationLangModel?
        get() = annotations.find { it.hasType<IntoSet>() }?.let { CtIntoCollectionAnnotationImpl(it) }
}