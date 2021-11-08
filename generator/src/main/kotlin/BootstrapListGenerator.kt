package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.BootstrapListBinding
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.Names
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.inject.Provider
import javax.lang.model.element.Modifier.PRIVATE

internal class BootstrapListGenerator(
    private val bootstrapListBindings: Collection<BootstrapListBinding>,
    methodNs: Namespace,
    private val thisGraph: BindingGraph,
    private val provisionGenerator: Provider<ProvisionGenerator>,
    private val generators: Generators,
) : ComponentGenerator.Contributor {
    private val accessors = bootstrapListBindings.associateWith { methodNs.name(it.target.name) }

    fun generateCreation(builder: ExpressionBuilder, binding: BootstrapListBinding) {
        with(builder) {
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
                                val gen = generators[nodeBinding.owner].conditionGenerator
                                gen.expression(this, nodeBinding.conditionScope)
                            }
                            controlFlow("if (%L) ".formatCode(expression)) {
                                +buildExpression {
                                    +"list.add("
                                    provisionGenerator.get().generateAccess(this, nodeBinding, DependencyKind.Direct)
                                    +")"
                                }
                            }
                        }
                    } else {
                        +buildExpression {
                            +"list.add("
                            provisionGenerator.get().generateAccess(this, nodeBinding, DependencyKind.Direct)
                            +")"
                        }
                    }
                }
                +"return list"
            }
        }
    }
}