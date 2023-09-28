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

open class ExpressionBuilder {
    @PublishedApi
    internal val impl: CodeBlock.Builder = CodeBlock.builder()

    operator fun CodeBlock.unaryPlus() {
        impl.add(this)
    }

    operator fun String.unaryPlus() {
        require('$' !in this) {
            "String \"$this\" contains control characters. Use %L placeholder."
        }
        impl.add(this)
    }

    inline fun <T> join(
        seq: Iterable<T>,
        separator: String = ", ",
        crossinline block: ExpressionBuilder.(T) -> Unit
    ) {
        impl.add(CodeBlock.join(seq.map { buildExpression { block(it) } }, separator))
    }

    fun String.formatCode(vararg args: Any): CodeBlock {
        return CodeBlock.of(this.replace('%', '$'), *args)
    }
}