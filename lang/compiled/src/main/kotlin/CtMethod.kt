package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.IntoSet
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.lang.IntoCollectionAnnotationLangModel
import com.yandex.daggerlite.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.lang.common.MethodBase
import com.yandex.daggerlite.lang.hasType

abstract class CtMethod : MethodBase() {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    final override val providesAnnotationIfPresent: ProvidesAnnotationLangModel?
        get() = annotations.find { it.hasType<Provides>() }?.let { CtProvidesAnnotationImpl(it) }

    final override val intoListAnnotationIfPresent: IntoCollectionAnnotationLangModel?
        get() = annotations.find { it.hasType<IntoList>() }?.let { CtIntoCollectionAnnotationImpl(it) }

    final override val intoSetAnnotationIfPresent: IntoCollectionAnnotationLangModel?
        get() = annotations.find { it.hasType<IntoSet>() }?.let { CtIntoCollectionAnnotationImpl(it) }
}