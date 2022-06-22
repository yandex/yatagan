package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.generator.lang.CtAnnotatedLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel

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