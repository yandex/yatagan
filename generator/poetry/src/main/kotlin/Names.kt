package com.yandex.daggerlite.generator.poetry

import com.squareup.javapoet.ClassName

object Names {
    val Lazy: ClassName = ClassName.get(com.yandex.daggerlite.Lazy::class.java)
    val Provider: ClassName = ClassName.get(javax.inject.Provider::class.java)
    val Optional: ClassName = ClassName.get(com.yandex.daggerlite.Optional::class.java)
    val AssertionError: ClassName = ClassName.get(java.lang.AssertionError::class.java)
    val Arrays: ClassName = ClassName.get(java.util.Arrays::class.java)
}
