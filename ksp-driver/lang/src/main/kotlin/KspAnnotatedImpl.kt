package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.daggerlite.core.lang.memoize

internal fun annotationsFrom(impl: KSAnnotated) = impl.annotations.map(::KspAnnotationImpl).memoize()