package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtAnnotationBase

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