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