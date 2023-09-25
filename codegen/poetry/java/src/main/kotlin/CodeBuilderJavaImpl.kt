package com.yandex.yatagan.codegen.poetry.java

import com.squareup.javapoet.CodeBlock
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.lang.Method

internal class CodeBuilderJavaImpl(
    private val builder: CodeBlock.Builder = CodeBlock.builder(),
) : CodeBuilder {
    fun build(): CodeBlock = builder.build()

    override fun appendStatement(block: ExpressionBuilder.() -> Unit) = apply {
        builder.addStatement(buildExpression(block))
    }

    override fun appendAssignment(
        receiver: (ExpressionBuilder.() -> Unit)?,
        setter: Method,
        value: ExpressionBuilder.() -> Unit,
    ) = apply {
        builder.addStatement(
            CodeBlock.builder().apply {
                if (receiver != null) {
                    add(buildExpression(receiver))
                    add(".")
                }
                add("\$N(\$L)", setter.name, buildExpression(value))
            }.build()
        )
    }

    override fun appendVariableDeclaration(
        type: TypeName,
        name: String,
        mutable: Boolean,
        initializer: ExpressionBuilder.() -> Unit,
    ) = apply {
        builder.addStatement(CodeBlock.builder().apply {
            if (!mutable) {
                add("final ")
            }
            add("\$T \$N = ", JavaTypeName(type), name)
                .add(buildExpression(initializer))
        }.build())
    }

    override fun appendReturnStatement(value: ExpressionBuilder.() -> Unit) = apply {
        builder.addStatement("return \$L", buildExpression(value))
    }

    override fun appendIfControlFlow(
        condition: ExpressionBuilder.() -> Unit,
        ifTrue: CodeBuilder.() -> Unit,
        ifFalse: (CodeBuilder.() -> Unit)?,
    ) = apply {
        builder.beginControlFlow("if (\$L)", buildExpression(condition))
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
            builder.beginControlFlow("if (\$L)", buildExpression { condition(firstArg) })
            block(firstArg)
        }
        iterator.forEach { arg ->
            builder.nextControlFlow("else if (\$L)", buildExpression { condition(arg) })
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
        builder.beginControlFlow("switch (\$L)", buildExpression(subject))
        for (i in 0..<numberOfCases) {
            builder.beginControlFlow("case \$L:", buildExpression { caseValue(i) })
            caseBlock(i)
//            builder.addStatement("break")
            builder.endControlFlow()
        }
        if (defaultCaseBlock != null) {
            builder.beginControlFlow("default:")
            defaultCaseBlock()
            builder.endControlFlow()
        }
        builder.endControlFlow()
    }

    override fun appendSynchronizedBlock(
        lock: ExpressionBuilder.() -> Unit,
        block: CodeBuilder.() -> Unit,
    ) = apply {
        builder.beginControlFlow("synchronized (\$L)", buildExpression(lock))
        block()
        builder.endControlFlow()
    }
}