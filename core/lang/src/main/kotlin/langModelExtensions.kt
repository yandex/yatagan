package com.yandex.daggerlite.core.lang

inline fun <reified A : Annotation> AnnotatedLangModel.isAnnotatedWith() = isAnnotatedWith(A::class.java)

inline fun <reified A : Annotation> AnnotatedLangModel.getAnnotation() = getAnnotation(A::class.java)

inline fun <reified A : Annotation> AnnotationLangModel.hasType() = hasType(A::class.java)