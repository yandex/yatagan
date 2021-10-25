package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.CompileTimeAnnotationLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal abstract class KspAnnotatedImpl : AnnotatedLangModel {
    protected abstract val impl: KSAnnotated

    override val annotations: Sequence<CompileTimeAnnotationLangModel> by lazy(NONE) {
        impl.annotations.map(::KspAnnotationImpl).memoize()
    }
}