package com.yandex.yatagan.codegen.poetry

interface Poetry {
    fun buildClass(
        name: ClassName,
        into: Appendable,
        block: TypeSpecBuilder.() -> Unit,
    )
}
