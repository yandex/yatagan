package com.yandex.yatagan.codegen.poetry.kotlin

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.jvm.volatile
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.FieldSpecBuilder

internal class FieldSpecBuilderKotlinImpl(
    private val builder: PropertySpec.Builder,
    defaultInitializer: CodeBlock?,
) : FieldSpecBuilder {
    init {
        if (defaultInitializer != null) {
            builder.initializer(defaultInitializer)
        }
    }

    fun build(): PropertySpec = builder.build()

    override fun volatile() {
        builder.volatile()
    }

    override fun initializer(block: ExpressionBuilder.() -> Unit) {
        builder.initializer(buildExpression(block))
    }
}