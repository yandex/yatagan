package com.yandex.daggerlite.generator.poetry

import com.squareup.javapoet.ClassName

object Names {
    val String: ClassName = ClassName.get(java.lang.String::class.java)
    val Integer: ClassName = ClassName.get(java.lang.Integer::class.java)
    val Long: ClassName = ClassName.get(java.lang.Long::class.java)
    val Boolean: ClassName = ClassName.get(java.lang.Boolean::class.java)
    val Character: ClassName = ClassName.get(java.lang.Character::class.java)
    val Byte: ClassName = ClassName.get(java.lang.Byte::class.java)

    val Lazy: ClassName = ClassName.get(com.yandex.daggerlite.Lazy::class.java)
    val Provider: ClassName = ClassName.get(javax.inject.Provider::class.java)
    val AssertionError = ClassName.get(java.lang.AssertionError::class.java)
}
