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

import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method

interface ExpressionBuilder {
    /**
     * Appends `<[className]>.this` for Java and `this@<[className]>` for Kotlin.
     */
    fun appendExplicitThis(
        className: ClassName,
    ) : ExpressionBuilder

    /**
     * Appends shared code literally
     */
    fun append(
        literalCode: String,
    ) : ExpressionBuilder

    fun appendString(
        string: String,
    ) : ExpressionBuilder

    fun appendType(
        type: TypeName,
    ) : ExpressionBuilder

    fun appendClassLiteral(
        type: TypeName,
    ) : ExpressionBuilder

    fun appendCast(
        asType: TypeName,
        expression: ExpressionBuilder.() -> Unit,
    ) : ExpressionBuilder

    fun appendObjectCreation(
        type: TypeName,
        argumentCount: Int = 0,
        argument: ExpressionBuilder.(index: Int) -> Unit = { throw AssertionError() },
        receiver: (ExpressionBuilder.() -> Unit)? = null,
    ) : ExpressionBuilder

    fun appendName(
        memberName: String,
    ) : ExpressionBuilder

    fun appendTypeCheck(
        expression: ExpressionBuilder.() -> Unit,
        type: TypeName,
    ) : ExpressionBuilder

    fun appendTernaryExpression(
        condition: ExpressionBuilder.() -> Unit,
        ifTrue: ExpressionBuilder.() -> Unit,
        ifFalse: ExpressionBuilder.() -> Unit,
    ) : ExpressionBuilder

    fun appendDotAndAccess(
        member: Member,
    ) : ExpressionBuilder

    fun appendCall(
        receiver: (ExpressionBuilder.() -> Unit)?,
        method: Method,
        argumentCount: Int,
        argument: ExpressionBuilder.(index: Int) -> Unit,
    ) : ExpressionBuilder

    fun coerceAsByte(
        expression: ExpressionBuilder.() -> Unit,
    ) : ExpressionBuilder

    fun appendCheckProvisionNotNull(
        expression: ExpressionBuilder.() -> Unit,
    ) : ExpressionBuilder

    fun appendCheckInputNotNull(
        expression: ExpressionBuilder.() -> Unit,
    ) : ExpressionBuilder

    fun appendReportUnexpectedBuilderInput(
        inputClassArgument: ExpressionBuilder.() -> Unit,
        expectedTypes: List<TypeName>,
    ) : ExpressionBuilder

    fun appendReportMissingBuilderInput(
        missingType: TypeName,
    ) : ExpressionBuilder
}