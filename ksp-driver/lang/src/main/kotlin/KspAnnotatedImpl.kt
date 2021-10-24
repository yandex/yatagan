package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.memoize
import kotlin.LazyThreadSafetyMode.NONE

abstract class KspAnnotatedImpl : AnnotatedLangModel {
    protected abstract val impl: KSAnnotated

    override val annotations: Sequence<AnnotationLangModel> by lazy(NONE) {
        impl.annotations.map(::KspAnnotationImpl).memoize()
    }
}