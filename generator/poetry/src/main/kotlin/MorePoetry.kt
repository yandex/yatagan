// Copyright 2020 Yandex LLC. All rights reserved.

package com.yandex.dagger3.generator.poetry

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.ExecutableElement

class ClassTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    @PublishedApi
    override val impl: TypeSpec.Builder = TypeSpec.classBuilder(name)
}

class InterfaceTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    @PublishedApi
    override val impl: TypeSpec.Builder = TypeSpec.interfaceBuilder(name)
}

class AnnotationTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    @PublishedApi
    override val impl: TypeSpec.Builder = TypeSpec.annotationBuilder(name)
}

class MethodSpecBuilderImpl(name: String) : MethodSpecBuilder() {
    @PublishedApi
    override val impl: MethodSpec.Builder = MethodSpec.methodBuilder(name)
}

class ConstructorSpecBuilder : MethodSpecBuilder() {
    @PublishedApi
    override val impl: MethodSpec.Builder = MethodSpec.constructorBuilder()
}

class OverrideMethodSpecBuilder(base: ExecutableElement) : MethodSpecBuilder() {
    @PublishedApi
    override val impl: MethodSpec.Builder = MethodSpec.overriding(base)
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
