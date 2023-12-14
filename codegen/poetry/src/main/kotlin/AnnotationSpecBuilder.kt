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
import kotlin.reflect.KClass

@JavaPoetry
class AnnotationSpecBuilder private constructor(
    @PublishedApi
    internal val impl: AnnotationSpec.Builder,
) {
    constructor(clazz: KClass<out Annotation>) : this(AnnotationSpec.builder(clazz.java))
    constructor(className: ClassName) : this(AnnotationSpec.builder(className))

    inline fun <reified E : Enum<E>> enumValue(value: E, name: String = "value") {
        impl.addMember(name, "\$T.\$N", E::class.java, value.name)
    }

    fun classValue(type: ClassName, name: String = "value") {
        impl.addMember(name, "\$T.class", type)
    }

    fun classValues(vararg types: ClassName, name: String = "value") {
        value(name) {
            +"{"
            join(types.asIterable()) { type -> +"%T.class".formatCode(type) }
            +"}"
        }
    }

    inline fun value(name: String = "value", block: ExpressionBuilder.() -> Unit) {
        impl.addMember(name, buildExpression(block))
    }

    fun stringValue(value: String, name: String = "value") {
        impl.addMember(name, buildExpression { +"%S".formatCode(value) })
    }

    fun stringValues(vararg values: String, name: String = "value") {
        value(name) {
            +"{"
            join(values.asIterable()) { value -> +"%S".formatCode(value) }
            +"}"
        }
    }

    inline fun <reified A : Annotation> annotation(
        block: AnnotationSpecBuilder.() -> Unit = {}
    ): AnnotationSpec {
        return AnnotationSpecBuilder(A::class).apply(block).impl.build()
    }
}