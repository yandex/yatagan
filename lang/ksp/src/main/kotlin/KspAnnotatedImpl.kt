package com.yandex.daggerlite.lang.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.lang.compiled.CtAnnotatedLangModel
import com.yandex.daggerlite.lang.compiled.CtAnnotationLangModel

internal class KspAnnotatedImpl<T : KSAnnotated>(
     val impl: T
) : CtAnnotatedLangModel {
    override val annotations: Sequence<CtAnnotationLangModel> by lazy {
        impl.annotations.map { KspAnnotationImpl(it) }.memoize()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}