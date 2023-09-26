package com.yandex.yatagan.codegen.poetry.kotlin

import com.squareup.kotlinpoet.CodeBlock
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName

internal class CodeBuilderKotlinImpl(
    private val builder: CodeBlock.Builder = CodeBlock.builder(),
) : CodeBuilder {
    fun build(): CodeBlock = builder.build()

    override fun appendStatement(block: ExpressionBuilder.() -> Unit) = apply {
        builder.addStatement("%L", buildExpression(block))
    }

    override fun appendVariableDeclaration(
        type: TypeName,
        name: String,
        mutable: Boolean,
        initializer: ExpressionBuilder.() -> Unit,
    ) = apply {
        builder.addStatement("%L", CodeBlock.builder().apply {
            if (mutable) {
                add("var ")
            } else {
                add("val ")
            }
            add("%N: %T = ", name, KotlinTypeName(type))
            ExpressionBuilderKotlinImpl(this).apply(initializer)
        }.build())
    }

    override fun appendReturnStatement(value: ExpressionBuilder.() -> Unit) = apply {
        builder.addStatement("return %L", buildExpression(value))
    }

    override fun appendIfControlFlow(
        condition: ExpressionBuilder.() -> Unit,
        ifTrue: CodeBuilder.() -> Unit,
        ifFalse: (CodeBuilder.() -> Unit)?,
    ) = apply {
        builder.beginControlFlow("if (%L)", buildExpression(condition))
        ifTrue()
        if (ifFalse != null) {
            builder.nextControlFlow("else")
            ifFalse()
        }
        builder.endControlFlow()
    }

    override fun <T> appendIfElseIfControlFlow(
        args: Iterable<T>,
        condition: ExpressionBuilder.(arg: T) -> Unit,
        block: CodeBuilder.(arg: T) -> Unit,
        fallback: CodeBuilder.() -> Unit,
    ) = apply {
        val iterator = args.iterator()
        iterator.next().let { firstArg ->
            builder.beginControlFlow("if (%L)", buildExpression { condition(firstArg) })
            block(firstArg)
        }
        iterator.forEach { arg ->
            builder.nextControlFlow("else if (%L)", buildExpression { condition(arg) })
            block(arg)
        }
        builder.nextControlFlow("else")
        fallback()
        builder.endControlFlow()
    }

    override fun appendSwitchingControlFlow(
        subject: ExpressionBuilder.() -> Unit,
        numberOfCases: Int,
        caseValue: ExpressionBuilder.(index: Int) -> Unit,
        caseBlock: CodeBuilder.(index: Int) -> Unit,
        defaultCaseBlock: (CodeBuilder.() -> Unit)?,
    ) = apply {
        builder.beginControlFlow("when (%L)", buildExpression(subject))
        for (i in 0..<numberOfCases) {
            builder.beginControlFlow("%L -> ", buildExpression { caseValue(i) })
            caseBlock(i)
            builder.endControlFlow()
        }
        if (defaultCaseBlock != null) {
            builder.beginControlFlow("else -> ")
            defaultCaseBlock()
            builder.endControlFlow()
        }
        builder.endControlFlow()
    }

    override fun appendSynchronizedBlock(
        lock: ExpressionBuilder.() -> Unit,
        block: CodeBuilder.() -> Unit,
    ) = apply {
        builder.beginControlFlow("synchronized (%L)", buildExpression(lock))
        block()
        builder.endControlFlow()
    }
}