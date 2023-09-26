package com.yandex.yatagan.codegen.poetry.kotlin

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.MethodSpecBuilder
import com.yandex.yatagan.codegen.poetry.TypeName

internal class MethodSpecBuilderKotlinImpl(
    private val builder: FunSpec.Builder,
) : MethodSpecBuilder {
    fun build(): FunSpec = builder.build()

    override fun returnType(type: TypeName) {
        builder.returns(KotlinTypeName(type))
    }

    override fun parameter(type: TypeName, name: String) {
        builder.addParameter(name, KotlinTypeName(type))
    }

    override fun manualOverride() {
        builder.addModifiers(KModifier.OVERRIDE)
    }

    override fun generic(i: TypeName.TypeVariable) {
        builder.addTypeVariable(TypeVariableName(i))
    }

    override fun code(block: CodeBuilder.() -> Unit) {
        builder.addCode(buildCode(block))
    }
}