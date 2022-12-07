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

@JavaPoetry
open class CodeBuilder {
    @PublishedApi
    internal val implCode: CodeBlock.Builder = CodeBlock.builder()

    operator fun CodeBlock.unaryPlus() {
        implCode.addStatement(this)
    }

    operator fun String.unaryPlus() {
        implCode.addStatement(this)
    }

    fun String.formatCode(vararg args: Any): CodeBlock {
        return CodeBlock.of(this.replace('%', '$'), *args)
    }

    inline fun controlFlow(code: CodeBlock, block: CodeBuilder.() -> Unit) {
        implCode.beginControlFlow("\$L", code)
        block()
        implCode.endControlFlow()
    }

    inline fun controlFlow(code: String, block: CodeBuilder.() -> Unit) {
        implCode.beginControlFlow("\$L", code)
        block()
        implCode.endControlFlow()
    }
}