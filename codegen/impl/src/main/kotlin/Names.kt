package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.ClassName

internal object Names {
    val Lazy: ClassName = ClassName.get(com.yandex.yatagan.Lazy::class.java)
    val Provider: ClassName = ClassName.get(javax.inject.Provider::class.java)
    val Optional: ClassName = ClassName.get(com.yandex.yatagan.Optional::class.java)
    val ThreadAssertions: ClassName = ClassName.get(com.yandex.yatagan.ThreadAssertions::class.java)
    val AssertionError: ClassName = ClassName.get(java.lang.AssertionError::class.java)
    val ArrayList: ClassName = ClassName.get(java.util.ArrayList::class.java)
    val HashMap: ClassName = ClassName.get(java.util.HashMap::class.java)
    val HashSet: ClassName = ClassName.get(java.util.HashSet::class.java)
    val Checks: ClassName = ClassName.get("com.yandex.yatagan.internal", "Checks")
}
