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

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier
import kotlin.reflect.KClass

@JavaPoetry
abstract class TypeSpecBuilder : AnnotatibleBuilder {
    @PublishedApi
    internal abstract val impl: TypeSpec.Builder

    fun method(builder: MethodSpecBuilder) {
        impl.addMethod(builder.implBuild())
    }

    inline fun method(name: String, block: MethodSpecBuilder.() -> Unit) {
        method(methodBuilder(name).apply(block))
    }

    fun methodBuilder(name: String): MethodSpecBuilder {
        return MethodSpecBuilderImpl(name)
    }

    inline fun constructor(block: MethodSpecBuilder.() -> Unit) {
        method(constructorBuilder().apply(block))
    }

    fun constructorBuilder(): MethodSpecBuilder = ConstructorSpecBuilder()

    inline fun field(type: TypeName, name: String, block: FieldSpecBuilder.() -> Unit = {}) {
        impl.addField(FieldSpecBuilder(type, name).apply(block).impl.build())
    }

    inline fun <reified A : Annotation> annotation(block: AnnotationSpecBuilder.() -> Unit = {}) {
        impl.addAnnotation(AnnotationSpecBuilder(A::class).apply(block).impl.build())
    }

    override fun annotation(mirror: AnnotationMirror) {
        impl.addAnnotation(AnnotationSpec.get(mirror))
    }

    override fun annotation(
        clazz: KClass<out Annotation>,
        block: AnnotationSpecBuilder.() -> Unit,
    ) {
        impl.addAnnotation(AnnotationSpecBuilder(clazz).apply(block).impl.build())
    }

    fun annotation(name: ClassName) {
        impl.addAnnotation(name)
    }

    fun implements(typeName: TypeName) {
        impl.addSuperinterface(typeName)
    }

    fun extends(typeName: TypeName) {
        impl.superclass(typeName)
    }

    fun originatingElement(element: Element) {
        impl.addOriginatingElement(element)
    }

    inline fun nestedType(block: () -> TypeSpec) {
        impl.addType(block())
    }

    fun modifiers(vararg modifiers: Modifier) {
        impl.addModifiers(*modifiers)
    }

    fun generic(vararg typeVars: TypeVariableName) {
        typeVars.forEach(impl::addTypeVariable)
    }
}