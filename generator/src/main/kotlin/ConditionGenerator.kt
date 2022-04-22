package com.yandex.daggerlite.generator

import com.squareup.javapoet.TypeName
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.BindingGraph.LiteralUsage
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.normalized
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE

internal class ConditionGenerator(
    private val fieldsNs: Namespace,
    private val methodsNs: Namespace,
    private val thisGraph: BindingGraph,
) : ComponentGenerator.Contributor {
    private val literalAccess: Map<ConditionScope.Literal, ConditionAccessStrategy> = run {
        thisGraph.localConditionLiterals.mapValues { (literal, usage) ->
            when (usage) {
                LiteralUsage.Eager -> EagerAccessStrategy(literal)
                LiteralUsage.Lazy -> LazyAccessStrategy(literal)
            }
        }
    }

    private fun access(
        literal: ConditionScope.Literal,
        builder: ExpressionBuilder,
        inside: BindingGraph,
    ) {
        require(!literal.negated) { "Not reached: must be normalized" }

        val localLiteralAccess = literalAccess[literal]
        if (localLiteralAccess != null) {
            localLiteralAccess.access(builder = builder, inside = inside)
        } else {
            thisGraph.parent!!.let(Generators::get).conditionGenerator.access(literal, builder, inside)
        }
    }

    override fun generate(builder: TypeSpecBuilder) {
        for (it in literalAccess.values) {
            it.generateInComponent(builder)
        }
    }

    fun expression(
        builder: ExpressionBuilder,
        conditionScope: ConditionScope,
        inside: BindingGraph,
    ) = with(builder) {
        join(conditionScope.expression, separator = " && ") { clause ->
            +"("
            join(clause, separator = " || ") { literal ->
                if (literal.negated) {
                    +"!"
                }
                access(literal = literal.normalized(), builder = this, inside = inside)
            }
            +")"
        }
    }

    private interface ConditionAccessStrategy {
        fun generateInComponent(builder: TypeSpecBuilder)
        fun access(builder: ExpressionBuilder, inside: BindingGraph)
    }

    /**
     * Single final boolean field, eagerly initialized.
     */
    private inner class EagerAccessStrategy(
        private val literal: ConditionScope.Literal,
    ) : ConditionAccessStrategy {

        private val name = fieldsNs.name(
            nameModel = literal.root.asType().name,
            suffix = literal.path.joinToString(separator = "_") { it.name },
        )

        override fun generateInComponent(builder: TypeSpecBuilder) {
            with(builder) {
                field(TypeName.BOOLEAN, name) {
                    modifiers(PRIVATE, FINAL)
                    initializer {
                        genEvaluateLiteral(literal = literal, builder = this)
                    }
                }
            }
        }

        override fun access(builder: ExpressionBuilder, inside: BindingGraph) {
            with(builder) {
                +componentInstance(inside = inside, graph = thisGraph)
                +"."
                +name
            }
        }
    }

    /**
     * Single byte field, lazily initialized (three states).
     */
    private inner class LazyAccessStrategy(
        private val literal: ConditionScope.Literal,
    ) : ConditionAccessStrategy {
        private val name = fieldsNs.name(
            nameModel = literal.root.asType().name,
            suffix = literal.path.joinToString(separator = "_") { it.name },
        )
        private val accessorName = methodsNs.name(
            nameModel = literal.root.asType().name,
            suffix = literal.path.joinToString(separator = "_") { it.name },
        )

        override fun generateInComponent(builder: TypeSpecBuilder) {
            with(builder) {
                field(TypeName.BYTE, name) {
                    modifiers(PRIVATE)
                }
                method(accessorName) {
                    modifiers(PRIVATE)
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

        override fun access(builder: ExpressionBuilder, inside: BindingGraph) {
            with(builder) {
                +componentInstance(inside = inside, graph = thisGraph)
                +"."
                +accessorName
                +"()"
            }
        }
    }

    companion object {
        private fun genEvaluateLiteral(literal: ConditionScope.Literal, builder: ExpressionBuilder) {
            require(!literal.negated) { "Not reached: must be normalized" }
            with(builder) {
                val rootType = literal.root.asType()
                literal.path.asSequence().forEachIndexed { index, member ->
                    if (index == 0) {
                        when (rootType.declaration.kotlinObjectKind) {
                            KotlinObjectKind.Object -> {
                                +"%T.INSTANCE.%N".formatCode(rootType.typeName(), member.name)
                            }
                            else -> {
                                +"%T.%N".formatCode(rootType.typeName(), member.name)
                            }
                        }
                        if (member is FunctionLangModel) +"()"
                    } else {
                        +".%N".formatCode(member.name)
                        if (member is FunctionLangModel) +"()"
                    }
                }
            }
        }
    }
}
