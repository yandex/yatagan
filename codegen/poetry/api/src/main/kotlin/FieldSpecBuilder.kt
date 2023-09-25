package com.yandex.yatagan.codegen.poetry

interface FieldSpecBuilder {
    fun volatile()

    fun initializer(block: ExpressionBuilder.() -> Unit)
}