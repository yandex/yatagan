package com.yandex.daggerlite.rt.lang

val Annotation.javaAnnotationClass: Class<out Annotation>
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    get() = (this as java.lang.annotation.Annotation).annotationType()