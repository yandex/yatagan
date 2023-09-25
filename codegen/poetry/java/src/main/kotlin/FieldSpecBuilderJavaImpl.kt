package com.yandex.yatagan.codegen.poetry.java

import com.squareup.javapoet.FieldSpec
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.FieldSpecBuilder
import javax.lang.model.element.Modifier

internal class FieldSpecBuilderJavaImpl(
    private val builder: FieldSpec.Builder,
) : FieldSpecBuilder {
    fun build(): FieldSpec = builder.build()

    override fun volatile() {
        builder.addModifiers(Modifier.VOLATILE)
    }

    override fun initializer(block: ExpressionBuilder.() -> Unit) {
        builder.initializer(buildExpression(block))
    }
}