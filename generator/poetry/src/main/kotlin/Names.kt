package com.yandex.dagger3.generator.poetry

import com.squareup.javapoet.ClassName

object Names {
    val Lazy: ClassName = ClassName.get(dagger.Lazy::class.java)
    val Provider: ClassName = ClassName.get(javax.inject.Provider::class.java)
    val AssertionError = ClassName.get(java.lang.AssertionError::class.java)
}