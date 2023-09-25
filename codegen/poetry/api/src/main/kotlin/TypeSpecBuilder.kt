package com.yandex.yatagan.codegen.poetry

import com.yandex.yatagan.lang.Method

interface TypeSpecBuilder {
    fun addSuppressWarningsAnnotation(
        vararg warnings: String,
    )

    fun implements(name: TypeName)

    fun primaryConstructor(
        access: Access,
        block: MethodSpecBuilder.() -> Unit,
    )

    fun field(
        type: TypeName,
        name: String,
        isMutable: Boolean,
        access: Access,
        block: FieldSpecBuilder.() -> Unit,
    )

    fun method(
        name: String,
        access: Access,
        isStatic: Boolean = false,
        block: MethodSpecBuilder.() -> Unit,
    )

    fun overrideMethod(
        method: Method,
        block: MethodSpecBuilder.() -> Unit,
    )

    fun nestedClass(
        name: ClassName,
        access: Access,
        isInner: Boolean,
        block: TypeSpecBuilder.() -> Unit,
    )
}