package com.yandex.yatagan.codegen.poetry

interface MethodSpecBuilder {
    fun returnType(type: TypeName)

    fun parameter(
        type: TypeName,
        name: String,
    )

    fun manualOverride()

    fun generic(i: TypeName.TypeVariable)

    fun code(block: CodeBuilder.() -> Unit)
}