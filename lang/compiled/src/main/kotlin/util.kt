package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.Annotation

internal inline fun <reified A : kotlin.Annotation> Annotation.hasType() = annotationClass.isClass(A::class.java)
