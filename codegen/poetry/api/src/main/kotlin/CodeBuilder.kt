package com.yandex.yatagan.codegen.poetry

import com.yandex.yatagan.lang.Method

interface CodeBuilder {
    fun appendStatement(
        block: ExpressionBuilder.() -> Unit,
    ) : CodeBuilder

    fun appendAssignment(
        receiver: (ExpressionBuilder.() -> Unit)?,
        setter: Method,
        value: ExpressionBuilder.() -> Unit,
    ) : CodeBuilder

    fun appendVariableDeclaration(
        type: TypeName,
        name: String,
        mutable: Boolean,
        initializer: ExpressionBuilder.() -> Unit,
    ) : CodeBuilder

    fun appendReturnStatement(
        value: ExpressionBuilder.() -> Unit,
    ) : CodeBuilder

    fun appendIfControlFlow(
        condition: ExpressionBuilder.() -> Unit,
        ifTrue: CodeBuilder.() -> Unit,
        ifFalse: (CodeBuilder.() -> Unit)? = null,
    ) : CodeBuilder

    fun<T> appendIfElseIfControlFlow(
        args: Iterable<T>,
        condition: ExpressionBuilder.(arg: T) -> Unit,
        block: CodeBuilder.(arg: T) -> Unit,
        fallback: CodeBuilder.() -> Unit,
    ) : CodeBuilder

    fun appendSwitchingControlFlow(
        subject: ExpressionBuilder.() -> Unit,
        numberOfCases: Int,
        caseValue: ExpressionBuilder.(index: Int) -> Unit,
        caseBlock: CodeBuilder.(index: Int) -> Unit,
        defaultCaseBlock: (CodeBuilder.() -> Unit)?,
    ) : CodeBuilder

    fun appendSynchronizedBlock(
        lock: ExpressionBuilder.() -> Unit,
        block: CodeBuilder.() -> Unit,
    ) : CodeBuilder

}