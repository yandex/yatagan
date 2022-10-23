package com.yandex.daggerlite.codegen.impl

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import com.yandex.daggerlite.codegen.poetry.ExpressionBuilder
import com.yandex.daggerlite.codegen.poetry.TypeSpecBuilder
import com.yandex.daggerlite.codegen.poetry.buildExpression
import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.BindingGraph.LiteralUsage
import com.yandex.daggerlite.core.graph.Extensible
import com.yandex.daggerlite.core.graph.normalized
import com.yandex.daggerlite.core.model.ConditionModel
import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.lang.FunctionLangModel
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE

internal class ConditionGenerator(
    private val fieldsNs: Namespace,
    private val methodsNs: Namespace,
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
        require(!literal.negated) { "Not reached: must be normalized" }

        val localLiteralAccess = literalAccess[literal]
        if (localLiteralAccess != null) {
            localLiteralAccess.access(
                builder = builder,
                inside = inside,
                isInsideInnerClass = isInsideInnerClass,
            )
        } else {
            thisGraph.parent!![ConditionGenerator].access(
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
        conditionScope: ConditionScope,
        inside: BindingGraph,
        isInsideInnerClass: Boolean,
    ) = with(builder) {
        join(conditionScope.expression, separator = " && ") { clause ->
            +"("
            join(clause, separator = " || ") { literal ->
                if (literal.negated) {
                    +"!"
                }
                access(
                    literal = literal.normalized(),
                    builder = this,
                    inside = inside,
                    isInsideInnerClass = isInsideInnerClass,
                )
            }
            +")"
        }
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
        require(!literal.negated) { "Not reached: must be normalized" }
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
                if (member is FunctionLangModel) +"()"
            }
        }
    }

    companion object Key : Extensible.Key<ConditionGenerator> {
        override val keyType get() = ConditionGenerator::class.java
    }
}

internal object ComponentImplClassName : Extensible.Key<ClassName> {
    override val keyType get() = ClassName::class.java
}