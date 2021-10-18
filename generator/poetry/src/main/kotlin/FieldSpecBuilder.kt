package com.yandex.daggerlite.generator.poetry

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.TypeName
import javax.lang.model.element.Modifier

@JavaPoetry
class FieldSpecBuilder(type: TypeName, name: String) {
    @PublishedApi
    internal val impl: FieldSpec.Builder = FieldSpec.builder(type, name)

    fun modifiers(vararg modifiers: Modifier) {
        impl.addModifiers(*modifiers)
    }

    inline fun initializer(block: ExpressionBuilder.() -> Unit) {
        impl.initializer(buildExpression(block))
    }

    fun initializer(code: CodeBlock) {
        impl.initializer(code)
    }
}