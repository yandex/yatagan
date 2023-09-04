/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.core.model.impl.parsing

import com.yandex.yatagan.base.enumMapOf
import com.yandex.yatagan.base.enumSetOf
import com.yandex.yatagan.base.forEachBi
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Token.Kind.BeginOfExpression
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Token.Kind.BinaryAnd
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Token.Kind.BinaryOr
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Token.Kind.EndOfExpression
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Token.Kind.LeftParenthesis
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Token.Kind.RightParenthesis
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Token.Kind.UnaryNot
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Token.Kind.Variable

internal class BooleanExpressionParser<E>(
    private val expressionSource: String,
    private val factory: Factory<E>,
) {
    interface Factory<E> {
        sealed interface ParseResult<out E> {
            class Ok<E>(val variable: E) : ParseResult<E>
            class Error(val message: CharSequence) : ParseResult<Nothing>
        }

        fun createAnd(lhs: E, rhs: E): E
        fun createOr(lhs: E, rhs: E): E
        fun createNot(e: E): E
        fun parseVariable(text: String): ParseResult<E>
    }

    @Throws(ParseException::class)
    fun parse(): E {
        // 1. Lex the tokens
        val tokens = lex()

        // 2. Check the syntax, throw if invalid
        validate(tokens)

        // 3. Convert to inverse postfix, throw if semantic errors detected
        val inversePostfix = toInversePostfix(tokens)

        // 4. Build AST
        return buildExpressionTree(inversePostfix)
    }

    private fun lex(): List<Token> {
        val tokens = arrayListOf(
            Token(
                kind = BeginOfExpression,
                sourceLocation = 0,
                text = "",
            )
        )
        var variableIndex = -1
        val variableBuilder = StringBuilder()

        fun commitVariableTokenIfAny() {
            if (variableIndex >= 0) {
                tokens += Token(
                    kind = Variable,
                    sourceLocation = variableIndex,
                    text = variableBuilder.toString(),
                )
                variableBuilder.clear()
                variableIndex = -1
            }
        }

        expressionSource.forEachIndexed { index, char ->
            when (char) {
                '(' -> tokens += Token(
                    kind = LeftParenthesis,
                    sourceLocation = index,
                    text = "(",
                ).also { commitVariableTokenIfAny() }

                ')' -> tokens += Token(
                    kind = RightParenthesis,
                    sourceLocation = index,
                    text = ")",
                ).also { commitVariableTokenIfAny() }

                '|' -> tokens += Token(
                    kind = BinaryOr,
                    sourceLocation = index,
                    text = "|",
                ).also { commitVariableTokenIfAny() }

                '&' -> tokens += Token(
                    kind = BinaryAnd,
                    sourceLocation = index,
                    text = "&",
                ).also { commitVariableTokenIfAny() }

                '!' -> tokens += Token(
                    kind = UnaryNot,
                    sourceLocation = index,
                    text = "!",
                ).also { commitVariableTokenIfAny() }

                else -> when {
                    char.isWhitespace() -> {
                        commitVariableTokenIfAny()
                        return@forEachIndexed
                    }

                    char.isJavaIdentifierPart() || char == '.' || char == '@' || char == ':' -> {
                        if (variableIndex < 0) {
                            variableIndex = index
                        }
                        variableBuilder.append(char)
                    }

                    else -> throw ParseException(
                        source = expressionSource,
                        where = index,
                        what = "Unexpected character '$char'",
                    )
                }
            }
        }
        commitVariableTokenIfAny()
        tokens += Token(EndOfExpression, expressionSource.length, "")
        return tokens
    }

    private fun validate(tokens: List<Token>) {
        tokens.zipWithNext().forEachBi { current, next ->
            val allowed = checkNotNull(nextToken[current.kind])
            if (next.kind !in allowed) {
                val allowedString = allowed.joinToString { it.description }
                throw ParseException(
                    source = expressionSource,
                    where = next.sourceLocation,
                    what = "Unexpected $next. Expected one of: $allowedString",
                )
            }
        }
    }

    private fun toInversePostfix(tokens: List<Token>): List<Token> {
        val inversePostfix = ArrayList<Token>(tokens.size)
        val stack = arrayListOf<Token>()
        for (token in tokens) {
            when (token.kind) {
                BeginOfExpression -> {} // Skip
                LeftParenthesis -> stack += token
                RightParenthesis -> {
                    var foundLeft = false
                    while (stack.isNotEmpty()) {
                        val top = stack.removeLast()
                        if (top.kind == LeftParenthesis) {
                            foundLeft = true
                            break
                        }
                        inversePostfix += top
                    }
                    if (!foundLeft) {
                        throw ParseException(
                            source = expressionSource,
                            where = token.sourceLocation,
                            what = "Unmatched $token",
                        )
                    }
                }

                UnaryNot, BinaryAnd, BinaryOr -> {
                    while (stack.lastOrNull().let { top ->
                            when (val kind = top?.kind) {
                                // Operators
                                BinaryAnd, BinaryOr -> token.kind.ordinal >= kind.ordinal
                                UnaryNot -> token.kind != UnaryNot
                                else -> false
                            }
                        }) {
                        inversePostfix += stack.removeLast()
                    }
                    stack += token
                }

                Variable -> {
                    inversePostfix += token
                }

                EndOfExpression -> while (stack.isNotEmpty()) {
                    val top = stack.removeLast()
                    if (top.kind == LeftParenthesis) {
                        throw ParseException(
                            source = expressionSource,
                            where = top.sourceLocation,
                            what = "Unclosed $top",
                        )
                    }
                    inversePostfix += top
                }
            }
        }

        return inversePostfix
    }

    private fun buildExpressionTree(inversePostfix: List<Token>): E {
        val expressionStack = arrayListOf<E>()
        for (token in inversePostfix) {
            when (token.kind) {
                UnaryNot -> expressionStack.add(factory.createNot(expressionStack.removeLast()))
                BinaryAnd -> expressionStack.add(factory.createAnd(
                    rhs = expressionStack.removeLast(),
                    lhs = expressionStack.removeLast(),
                ))

                BinaryOr -> expressionStack.add(factory.createOr(
                    rhs = expressionStack.removeLast(),
                    lhs = expressionStack.removeLast(),
                ))

                Variable -> {
                    when (val result = factory.parseVariable(token.text)) {
                        is Factory.ParseResult.Error -> throw ParseException(
                            source = expressionSource,
                            where = token.sourceLocation..<token.sourceLocation + token.text.length - 1,
                            what = "Variable: ${result.message}"
                        )

                        is Factory.ParseResult.Ok ->
                            expressionStack.add(result.variable)
                    }
                }

                else -> throw AssertionError("Unexpected token in postfix notation - $token")
            }
        }
        return expressionStack.single()
    }

    private data class Token(
        val kind: Kind,
        val sourceLocation: Int,
        val text: String,
    ) {
        enum class Kind(val description: String) {
            BeginOfExpression("<begin-of-expression>"),
            LeftParenthesis("'('"),
            RightParenthesis("')'"),
            // region Operators - the lower the ordinal - the higher the priority.
            UnaryNot("'!'"),
            BinaryAnd("'&'"),
            BinaryOr("'|'"),
            // endregion
            Variable("<variable>"),
            EndOfExpression("<end-of-expression>"),
        }

        override fun toString(): String {
            return when (kind) {
                Variable -> "<variable '$text'>"
                else -> kind.description
            }
        }
    }

    companion object {
        private val nextToken: Map<Token.Kind, Set<Token.Kind>> = enumMapOf(
            BeginOfExpression to enumSetOf(
                Variable, UnaryNot, LeftParenthesis,
            ),
            LeftParenthesis to enumSetOf(
                Variable, UnaryNot, LeftParenthesis,
            ),
            RightParenthesis to enumSetOf(
                BinaryOr, BinaryAnd, RightParenthesis, EndOfExpression,
            ),
            BinaryAnd to enumSetOf(
                Variable, UnaryNot, LeftParenthesis,
            ),
            BinaryOr to enumSetOf(
                Variable, UnaryNot, LeftParenthesis,
            ),
            UnaryNot to enumSetOf(
                Variable, UnaryNot, LeftParenthesis,
            ),
            Variable to enumSetOf(
                BinaryOr, BinaryAnd, RightParenthesis, EndOfExpression,
            ),
            EndOfExpression to enumSetOf(),
        )
    }
}
