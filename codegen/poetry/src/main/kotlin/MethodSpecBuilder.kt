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
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeVariableName
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import kotlin.reflect.KClass

@JavaPoetry
abstract class MethodSpecBuilder : CodeBuilder(), AnnotatibleBuilder {
    @PublishedApi
    internal abstract val impl: MethodSpec.Builder

    internal fun implBuild(): MethodSpec {
        impl.addCode(implCode.build())
        return impl.build()
    }

    inline fun parameter(
        type: TypeName, name: String,
        block: ParameterSpecBuilder.() -> Unit = {}
    ) {
        impl.addParameter(ParameterSpecBuilder(type, name).apply(block).impl.build())
    }

    inline fun parameter(type: TypeName, name: Name, block: ParameterSpecBuilder.() -> Unit = {}) {
        parameter(type, name.toString(), block)
    }

    inline fun <reified A : Annotation> annotation(block: AnnotationSpecBuilder.() -> Unit = {}) {
        impl.addAnnotation(AnnotationSpecBuilder(A::class).apply(block).impl.build())
    }

    fun annotation(name: ClassName) {
        impl.addAnnotation(name)
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

    fun returnType(type: TypeName) {
        impl.returns(type)
    }

    fun modifiers(vararg modifiers: Modifier) {
        impl.addModifiers(*modifiers)
    }

    inline fun defaultValue(block: ExpressionBuilder.() -> Unit) {
        impl.defaultValue(buildExpression(block))
    }

    fun generic(vararg typeVars: TypeVariableName) {
        typeVars.forEach(impl::addTypeVariable)
    }
}