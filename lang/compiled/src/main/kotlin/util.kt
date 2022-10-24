package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.Annotation

internal inline fun <reified A : kotlin.Annotation> Annotation.hasType() = annotationClass.isClass(A::class.java)
