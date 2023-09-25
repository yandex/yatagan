package com.yandex.yatagan.codegen.poetry.java

import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeVariableName
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.MethodSpecBuilder
import com.yandex.yatagan.codegen.poetry.TypeName

internal class MethodSpecBuilderJavaImpl(
    private val builder: MethodSpec.Builder,
) : MethodSpecBuilder {
    fun build(): MethodSpec = builder.build()

    override fun returnType(type: TypeName) {
        builder.returns(JavaTypeName(type))
    }

    override fun parameter(type: TypeName, name: String) {
        builder.addParameter(JavaTypeName(type), name)
    }

    override fun manualOverride() {
        builder.addAnnotation(Override::class.java)
    }

    override fun generic(i: TypeName.TypeVariable) {
        builder.addTypeVariable(TypeVariableName.get(i.name))
    }

    override fun code(block: CodeBuilder.() -> Unit) {
        builder.addCode(buildCode(block))
    }
}