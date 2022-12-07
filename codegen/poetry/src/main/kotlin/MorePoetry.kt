/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
