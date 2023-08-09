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

package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.TypeName
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.BindingGraph.LiteralUsage
import com.yandex.yatagan.core.model.BooleanExpression
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.lang.Method
import javax.inject.Inject
import javax.inject.Singleton
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE

@Singleton
internal class ConditionGenerator @Inject constructor(
    @FieldsNamespace private val fieldsNs: Namespace,
    @MethodsNamespace private val methodsNs: Namespace,
    private val thisGraph: BindingGraph,
) : ComponentGenerator.Contributor {
    private val literalAccess: Map<ConditionModel, ConditionAccessStrategy> = run {
        thisGraph.localConditionLiterals.mapValues { (literal, usage) ->
            when (usage) {
                LiteralUsage.Eager -> EagerAccessStrategy(literal)
                LiteralUsage.Lazy -> LazyAccessStrategy(literal)
            }
        }
    }

    private fun access(
        literal: ConditionModel,
        builder: ExpressionBuilder,
        inside: BindingGraph,
        isInsideInnerClass: Boolean,
    ) {
        val localLiteralAccess = literalAccess[literal]
        if (localLiteralAccess != null) {
            localLiteralAccess.access(
                builder = builder,
                inside = inside,
                isInsideInnerClass = isInsideInnerClass,
            )
        } else {
            thisGraph.parent!![GeneratorComponent].conditionGenerator.access(
                literal = literal,
                builder = builder,
                inside = inside,
                isInsideInnerClass = isInsideInnerClass,
            )
        }
    }

    override fun generate(builder: TypeSpecBuilder) {
        for (it in literalAccess.values) {
            it.generateInComponent(builder)
        }
    }

    fun expression(
        builder: ExpressionBuilder,
        conditionScope: ConditionScope.ExpressionScope,
        inside: BindingGraph,
        isInsideInnerClass: Boolean,
    ) {
        conditionScope.expression.accept(BooleanExpressionGenerator(
            builder = builder,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        ))
    }

    private interface ConditionAccessStrategy {
        fun generateInComponent(builder: TypeSpecBuilder)
        fun access(builder: ExpressionBuilder, inside: BindingGraph, isInsideInnerClass: Boolean)
    }

    /**
     * Single final boolean field, eagerly initialized.
     */
    private inner class EagerAccessStrategy(
        private val literal: ConditionModel,
    ) : ConditionAccessStrategy {

        init {
            assert(!literal.requiresInstance) {
                "Eager strategy should not be used for non-static conditions"
            }
        }

        private val name = fieldsNs.name(
            nameModel = literal.root.type.name,
            suffix = literal.path.joinToString(separator = "_") { it.name },
        )

        override fun generateInComponent(builder: TypeSpecBuilder) {
            with(builder) {
                field(TypeName.BOOLEAN, name) {
                    modifiers(/*package-private*/ FINAL)
                    initializer {
                        genEvaluateLiteral(literal = literal, builder = this)
                    }
                }
            }
        }

        override fun access(builder: ExpressionBuilder, inside: BindingGraph, isInsideInnerClass: Boolean) {
            with(builder) {
                +"%L.%N".formatCode(
                    componentInstance(inside = inside, graph = thisGraph, isInsideInnerClass = isInsideInnerClass),
                    name,
                )
            }
        }
    }

    /**
     * Single byte field, lazily initialized (three states).
     */
    private inner class LazyAccessStrategy(
        private val literal: ConditionModel,
    ) : ConditionAccessStrategy {
        private val name = fieldsNs.name(
            nameModel = literal.root.type.name,
            suffix = literal.path.joinToString(separator = "_") { it.name },
        )
        private val accessorName = methodsNs.name(
            nameModel = literal.root.type.name,
            suffix = literal.path.joinToString(separator = "_") { it.name },
        )

        override fun generateInComponent(builder: TypeSpecBuilder) {
            with(builder) {
                field(TypeName.BYTE, name) {
                    modifiers(PRIVATE)  // PRIVATE: accessed only via its accessor.
                }
                method(accessorName) {
                    modifiers(/*package-private*/)
                    returnType(TypeName.BOOLEAN)
                    // NOTE: This implementation is not thread safe.
                    // In environments with multiple threads, this can lead to multiple condition computations,
                    //  which we can technically tolerate. The only "bad" case would be if these multiple computations
                    //  yield different results. While this seems pretty critical issue, we, as of now, choose not
                    //  to deal with it here, as code, that uses such overly dynamic conditions that may change value
                    //  in racy way, is presumed "incorrect".
                    controlFlow("if (this.%N == 0x0)".formatCode(name)) {
                        val expr = buildExpression {
                            genEvaluateLiteral(literal = literal, builder = this)
                        }
                        // 0x0 - uninitialized (default)
                        // 0x1 - true
                        // 0x2 - false.
                        +"this.%N = (byte) ((%L) ? 0x1 : 0x2)".formatCode(name, expr)
                    }
                    +"return this.%N == 0x1".formatCode(name)
                }
            }
        }

        override fun access(builder: ExpressionBuilder, inside: BindingGraph, isInsideInnerClass: Boolean) {
            with(builder) {
                +"%L.%N()".formatCode(componentInstance(
                    inside = inside,
                    graph = thisGraph,
                    isInsideInnerClass = isInsideInnerClass,
                ), accessorName)
            }
        }
    }

    private fun genEvaluateLiteral(literal: ConditionModel, builder: ExpressionBuilder) {
        with(builder) {
            val rootType = literal.root.type
            literal.path.asSequence().forEachIndexed { index, member ->
                if (index == 0) {
                    if (literal.requiresInstance) {
                        thisGraph.resolveBinding(literal.root).generateAccess(
                            builder = this,
                            inside = thisGraph,
                            isInsideInnerClass = false,
                        )
                    } else {
                        +"%T".formatCode(rootType.typeName())
                    }
                }
                +".%N".formatCode(member.name)
                if (member is Method) +"()"
            }
        }
    }

    private inner class BooleanExpressionGenerator(
        val builder: ExpressionBuilder,
        val inside: BindingGraph,
        val isInsideInnerClass: Boolean,
    ) : BooleanExpression.Visitor<Unit> {
        override fun visitVariable(variable: BooleanExpression.Variable) {
            access(
                literal = variable.model,
                builder = builder,
                inside = inside,
                isInsideInnerClass = isInsideInnerClass,
            )
        }

        override fun visitNot(not: BooleanExpression.Not) = with(builder) {
            +"!"
            val shouldUseParentheses = not.underlying.accept(object : BooleanExpression.Visitor<Boolean> {
                override fun visitVariable(variable: BooleanExpression.Variable) = false
                override fun visitNot(not: BooleanExpression.Not) = false
                override fun visitAnd(and: BooleanExpression.And) = true
                override fun visitOr(or: BooleanExpression.Or) = true
            })
            if (shouldUseParentheses) {
                +"("
            }
            not.underlying.accept(this@BooleanExpressionGenerator)
            if (shouldUseParentheses) {
                +")"
            }
        }

        override fun visitAnd(and: BooleanExpression.And) = with(builder) {
            val parenthesesUsageDetector = object : BooleanExpression.Visitor<Boolean> {
                override fun visitVariable(variable: BooleanExpression.Variable) = false
                override fun visitNot(not: BooleanExpression.Not) = false
                override fun visitAnd(and: BooleanExpression.And) = false
                override fun visitOr(or: BooleanExpression.Or) = true
            }
            val shouldUseParansForLhs = and.lhs.accept(parenthesesUsageDetector)
            val shouldUseParansForRhs = and.rhs.accept(parenthesesUsageDetector)

            if (shouldUseParansForLhs) {
                +"("
            }
            and.lhs.accept(this@BooleanExpressionGenerator)
            if (shouldUseParansForLhs) {
                +")"
            }

            +" && "

            if (shouldUseParansForRhs) {
                +"("
            }
            and.rhs.accept(this@BooleanExpressionGenerator)
            if (shouldUseParansForRhs) {
                +")"
            }
        }

        override fun visitOr(or: BooleanExpression.Or) {
            or.lhs.accept(this)
            with(builder) {
                +" || "
            }
            or.rhs.accept(this)
        }
    }
}
