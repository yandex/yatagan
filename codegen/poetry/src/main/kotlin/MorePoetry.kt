// Copyright 2020 Yandex LLC. All rights reserved.

package com.yandex.yatagan.codegen.poetry

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec

@PublishedApi
internal class ClassTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    @PublishedApi
    override val impl: TypeSpec.Builder = TypeSpec.classBuilder(name)
}

@PublishedApi
internal class InterfaceTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    @PublishedApi
    override val impl: TypeSpec.Builder = TypeSpec.interfaceBuilder(name)
}

@PublishedApi
internal class AnnotationTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    @PublishedApi
    override val impl: TypeSpec.Builder = TypeSpec.annotationBuilder(name)
}

internal class MethodSpecBuilderImpl(name: String) : MethodSpecBuilder() {
    override val impl: MethodSpec.Builder = MethodSpec.methodBuilder(name)
}

internal class ConstructorSpecBuilder : MethodSpecBuilder() {
    override val impl: MethodSpec.Builder = MethodSpec.constructorBuilder()
}

inline fun buildInterface(
    name: ClassName, block: TypeSpecBuilder.() -> Unit
): TypeSpec = InterfaceTypeSpecBuilder(name).apply(block).impl.build()

inline fun buildAnnotationClass(
    name: ClassName, block: TypeSpecBuilder.() -> Unit
): TypeSpec = AnnotationTypeSpecBuilder(name).apply(block).impl.build()

inline fun buildClass(
    name: ClassName, block: TypeSpecBuilder.() -> Unit
): TypeSpec = ClassTypeSpecBuilder(name).apply(block).impl.build()

inline fun buildExpression(block: ExpressionBuilder.() -> Unit): CodeBlock {
    return ExpressionBuilder().apply(block).impl.build()
}

operator fun ClassName.invoke(name: TypeName): ParameterizedTypeName {
    return ParameterizedTypeName.get(this, name)
}

object ArrayName {
    internal operator fun get(name: TypeName): ArrayTypeName {
        return ArrayTypeName.of(name)
    }
}
