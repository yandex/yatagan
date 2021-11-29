package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType
import javax.lang.model.element.Modifier.PRIVATE

internal class MultiBindingGenerator(
    private val multiBindings: Collection<MultiBinding>,
    methodNs: Namespace,
    private val thisGraph: BindingGraph,
) : ComponentGenerator.Contributor {
    private val accessors = multiBindings.associateWith { methodNs.name(it.target.name) }

    fun generateCreation(builder: ExpressionBuilder, binding: MultiBinding, inside: BindingGraph) {
        with(builder) {
            +componentForBinding(inside = inside, binding = binding)
            +"."
            +accessors[binding]!!
            +"()"
        }
    }

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        for (binding in multiBindings) {
            method(accessors[binding]!!) {
                modifiers(PRIVATE)
                returnType(binding.target.typeName())
                +"final %T list = new %T<>(${binding.contributions.size})"
                    .formatCode(binding.target.typeName(), Names.ArrayList)
                binding.contributions.forEach { (node: NodeModel, kind: ContributionType) ->
                    val nodeBinding = thisGraph.resolveBinding(node)
                    fun generateAccess() = buildExpression {
                        when(kind) {
                            ContributionType.Element -> +"list.add("
                            ContributionType.Collection -> +"list.addAll("
                        }
                        nodeBinding.generateAccess(builder = this, inside = thisGraph)
                        +")"
                    }

                    if (!nodeBinding.conditionScope.isAlways) {
                        if (nodeBinding.conditionScope.isNever) {
                            // Just skip this.
                        } else {
                            val expression = buildExpression {
                                val gen = Generators[nodeBinding.owner].conditionGenerator
                                gen.expression(this, nodeBinding.conditionScope)
                            }
                            controlFlow("if (%L) ".formatCode(expression)) {
                                +generateAccess()
                            }
                        }
                    } else {
                        +generateAccess()
                    }
                }
                +"return list"
            }
        }
    }
}