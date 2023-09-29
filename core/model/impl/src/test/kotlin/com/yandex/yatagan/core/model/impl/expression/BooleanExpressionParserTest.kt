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

package com.yandex.yatagan.core.model.impl.expression

import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser
import com.yandex.yatagan.core.model.impl.parsing.ParseException
import kotlin.test.Test
import kotlin.test.expect

class BooleanExpressionParserTest {
    private sealed interface ExpressionForTest {
        data class Not(
            val expression: ExpressionForTest,
        ) : ExpressionForTest {
            override fun toString() = "not($expression)"
        }

        data class And(
            val lhs: ExpressionForTest,
            val rhs: ExpressionForTest,
        ) : ExpressionForTest {
            override fun toString() = "and($lhs, $rhs)"
        }

        data class Or(
            val lhs: ExpressionForTest,
            val rhs: ExpressionForTest,
        ) : ExpressionForTest {
            override fun toString() = "or($lhs, $rhs)"
        }

        data class Variable(
            val name: String,
        ) : ExpressionForTest {
            override fun toString() = name
        }

        companion object FactoryForTest : BooleanExpressionParser.Factory<ExpressionForTest> {
            override fun createAnd(lhs: ExpressionForTest, rhs: ExpressionForTest) = And(lhs, rhs)
            override fun createOr(lhs: ExpressionForTest, rhs: ExpressionForTest) = Or(lhs, rhs)
            override fun createNot(e: ExpressionForTest) = Not(e)
            override fun parseVariable(text: String) = BooleanExpressionParser.Factory.ParseResult.Ok(Variable(text))
        }
    }

    private fun parse(expression: String): String {
        val underTest = BooleanExpressionParser(
            expressionSource = expression,
            factory = ExpressionForTest,
        )
        return underTest.parse().toString()
    }

    private fun parseError(expression: String): String? {
        return try {
            parse(expression)
            null
        } catch (e: ParseException) {
            e.formattedMessage.toString()
        }
    }

    @Test
    fun `successful parsing - simple variable names`() {
        expect("a") { parse("a") }
        expect("or(a, and(b, c))") { parse("a | b & c") }
        expect("or(or(a, b), c)") { parse("a | b | c") }
        expect("or(or(a, and(b, c)), d)") { parse("a | b & c | d") }
        expect("and(or(a, b), c)") { parse("(a | b) & c") }
        expect("and(or(a, b), c)") { parse("((((a) | b)) & (c))") }

        expect("or(not(a), and(not(b), not(c)))") { parse("!a | !b & !c") }
        expect("not(not(a))") { parse("!(!a)") }
        expect("not(not(a))") { parse("!!a") }
        expect("and(not(not(a)), b)") { parse("!!a & b") }
    }

    @Test
    fun `successful parsing - arbitrary variable name`() {
        expect("foo") { parse("foo") }
        expect("0foo:bar@:@1.2.3.4$") { parse("  0foo:bar@:@1.2.3.4$  ") }
        expect("and(0, 1)") { parse(" 0&1") }
    }

    @Test
    fun `forbidden characters`() {
        expect("""
            "a^b"
              ^~~~ Unexpected character '^'
        """.trimIndent()) { parseError("a^b") }
        expect("""
            "a,b"
              ^~~~ Unexpected character ','
        """.trimIndent()) { parseError("a,b") }
        expect("""
            "#b"
             ^~~~ Unexpected character '#'
        """.trimIndent()) { parseError("#b") }
        expect("""
            "a &                                     foo::%bar"
                              Unexpected character '%' ~~~^
        """.trimIndent()) { parseError("a &                                     foo::%bar") }
    }

    @Test
    fun `parsing error reporting`() {
        expect("""
            "(((((a) | b)) & (c))"
             ^~~~ Unclosed '('
        """.trimIndent()) {
            parseError("(((((a) | b)) & (c))")
        }

        expect("""
            "((((a) | b)) & (c))     )"
                    Unmatched ')' ~~~^
        """.trimIndent()) {
            parseError("((((a)\n|\tb)) & (c))\n    )")
        }

        expect("""
            "!"
              ^~~~ Unexpected <end-of-expression>. Expected one of: '(', '!', <variable>
        """.trimIndent()) {
            parseError("!")
        }

        expect("""
            ""
             ^~~~ Unexpected <end-of-expression>. Expected one of: '(', '!', <variable>
        """.trimIndent()) {
            parseError("")
        }

        expect("""
            "a | "
                 ^~~~ Unexpected <end-of-expression>. Expected one of: '(', '!', <variable>
        """.trimIndent()) {
            parseError("a | ")
        }

        expect("""
            "! | a"
               ^~~~ Unexpected '|'. Expected one of: '(', '!', <variable>
        """.trimIndent()) {
            parseError("! | a")
        }

        expect("""
            "a b"
               ^~~~ Unexpected <variable 'b'>. Expected one of: ')', '&', '|', <end-of-expression>
        """.trimIndent()) {
            parseError("a b")
        }

        expect("""
            "& a | b"
             ^~~~ Unexpected '&'. Expected one of: '(', '!', <variable>
        """.trimIndent()) {
            parseError("& a | b")
        }
    }
}