package com.yandex.daggerlite.lang.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.lang.compiled.CtAnnotated
import com.yandex.daggerlite.lang.compiled.CtAnnotationBase

internal class KspAnnotatedImpl<T : KSAnnotated>(
     val impl: T
) : CtAnnotated {
    override val annotations: Sequence<CtAnnotationBase> by lazy {
        impl.annotations.map { KspAnnotationImpl(it) }.memoize()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}