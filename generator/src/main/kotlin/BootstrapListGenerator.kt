package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.BootstrapListBinding
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.Names
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.lang.model.element.Modifier.PRIVATE

internal class BootstrapListGenerator(
    private val bootstrapListBindings: Collection<BootstrapListBinding>,
    methodNs: Namespace,
    private val thisGraph: BindingGraph,
) : ComponentGenerator.Contributor {
    private val accessors = bootstrapListBindings.associateWith { methodNs.name(it.target.name) }

    fun generateCreation(builder: ExpressionBuilder, binding: BootstrapListBinding, inside: BindingGraph) {
        with(builder) {
            +componentForBinding(inside = inside, binding = binding)
            +"."
            +accessors[binding]!!
            +"()"
        }
    }

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        for (binding in bootstrapListBindings) {
            method(accessors[binding]!!) {
                modifiers(PRIVATE)
                returnType(binding.target.typeName())
                +"final %T list = new %T<>(${binding.list.size})".formatCode(binding.target.typeName(), Names.ArrayList)
                binding.list.forEach { node ->
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