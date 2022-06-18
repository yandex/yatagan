package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.generator.lang.CtAnnotatedLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel

open class KspAnnotatedImpl(
    private val impl: KSAnnotated
) : CtAnnotatedLangModel {
    final override val annotations: Sequence<CtAnnotationLangModel> by lazy {
        impl.annotations.map { KspAnnotationImpl(it) }.memoize()
    }

    final override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}