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
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import javax.lang.model.element.AnnotationMirror
import kotlin.reflect.KClass

@JavaPoetry
class ParameterSpecBuilder constructor(type: TypeName, name: String) : AnnotatibleBuilder {
    @PublishedApi
    internal val impl: ParameterSpec.Builder = ParameterSpec.builder(type, name)

    inline fun <reified A : Annotation> annotation(
        block: AnnotationSpecBuilder.() -> Unit = {}
    ) {
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

    override fun annotation(
        className: ClassName,
        block: AnnotationSpecBuilder.() -> Unit,
    ) {
        impl.addAnnotation(AnnotationSpecBuilder(className).apply(block).impl.build())
    }
}