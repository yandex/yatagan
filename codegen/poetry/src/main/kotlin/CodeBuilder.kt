package com.yandex.daggerlite.codegen.poetry

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