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