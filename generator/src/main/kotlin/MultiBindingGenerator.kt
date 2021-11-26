package com.yandex.daggerlite.generator

import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.MultiBinding
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
                binding.contributions.forEach { node ->
                    val nodeBinding = thisGraph.resolveBinding(node)
                    if (!nodeBinding.conditionScope.isAlways) {
                        if (nodeBinding.conditionScope.isNever) {
                            // Just skip this.
                        } else {
                            val expression = buildExpression {
                                val gen = Generators[nodeBinding.owner].conditionGenerator
                                gen.expression(this, nodeBinding.conditionScope)
                            }
                            controlFlow("if (%L) ".formatCode(expression)) {
                                +buildExpression {
                                    +"list.add("
                                    nodeBinding.generateAccess(builder = this, inside = thisGraph)
                                    +")"
                                }
                            }
                        }
                    } else {
                        +buildExpression {
                            +"list.add("
                            nodeBinding.generateAccess(builder = this, inside = thisGraph)
                            +")"
                        }
                    }
                }
                +"return list"
            }
        }
    }
}