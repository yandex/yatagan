package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.ComponentDependencyBinding
import com.yandex.daggerlite.graph.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.graph.ComponentInstanceBinding
import com.yandex.daggerlite.graph.EmptyBinding
import com.yandex.daggerlite.graph.InstanceBinding
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.ProvisionBinding
import com.yandex.daggerlite.graph.SubComponentFactoryBinding

private class CreationGeneratorVisitor(
    private val builder: ExpressionBuilder,
    private val inside: BindingGraph,
) : Binding.Visitor<Unit> {
    override fun visitProvision(binding: ProvisionBinding) {
        with(builder) {
            generateCall(
                callable = binding.provision,
                arguments = binding.inputs.asIterable(),
                instance = if (binding.requiresModuleInstance) {
                    "%N.%N".formatCode(
                        componentForBinding(binding),
                        Generators[binding.owner].factoryGenerator.fieldNameFor(binding.originModule!!),
                    )
                } else null,
            ) { (node, kind) ->
                inside.resolveBinding(node).generateAccess(builder = this, inside = inside, kind = kind)
            }
        }
    }

    override fun visitInstance(binding: InstanceBinding) {
        with(builder) {
            val component = componentForBinding(binding)
            val factory = Generators[binding.owner].factoryGenerator
            +"$component.${factory.fieldNameFor(binding.target)}"
        }
    }

    override fun visitAlternatives(binding: AlternativesBinding) {
        with(builder) {
            var exhaustive = false
            for (alternative: NodeModel in binding.alternatives) {
                val altBinding = inside.resolveBinding(alternative)
                if (!altBinding.conditionScope.isAlways) {
                    if (altBinding.conditionScope.isNever) {
                        // Never scoped is, by definition, unreached, so just skip it.
                        continue
                    }
                    val expression = buildExpression {
                        val gen = Generators[inside].conditionGenerator
                        gen.expression(this, altBinding.conditionScope)
                    }
                    +"%L ? ".formatCode(expression)
                    altBinding.generateAccess(builder = builder, inside = inside)
                    +" : "
                } else {
                    altBinding.generateAccess(builder = builder, inside = inside)
                    exhaustive = true
                    break  // no further generation, the rest are (if any) unreachable.
                }
            }
            if (!exhaustive) {
                +"null /*empty*/"
            }
        }
    }

    override fun visitSubComponentFactory(binding: SubComponentFactoryBinding) {
        with(builder) {
            +"new %T(".formatCode(Generators[binding.targetGraph].factoryGenerator.implName)
            join(binding.targetGraph.usedParents) { parentGraph ->
                +buildExpression {
                    +componentInstance(inside = inside, graph = parentGraph)
                }
            }
            +")"
        }
    }

    override fun visitComponentDependency(binding: ComponentDependencyBinding) {
        with(builder) {
            val factory = Generators[binding.owner].factoryGenerator
            +factory.fieldNameFor(binding.dependency)
        }
    }

    override fun visitComponentInstance(binding: ComponentInstanceBinding) {
        with(builder) {
            +componentForBinding(binding)
        }
    }

    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding) {
        with(builder) {
            +"%N.%N.%N()".formatCode(
                componentForBinding(binding),
                Generators[binding.owner].factoryGenerator.fieldNameFor(binding.dependency),
                binding.getter.name,
            )
        }
    }

    override fun visitMulti(binding: MultiBinding) {
        Generators[binding.owner].multiBindingGenerator.generateCreation(
            builder = builder,
            binding = binding,
            inside = inside,
        )
    }

    override fun visitEmpty(binding: EmptyBinding) {
        throw AssertionError("Not reached")
    }

    private fun componentForBinding(binding: Binding): String {
        return componentForBinding(inside = inside, binding = binding)
    }
}

internal fun Binding.generateCreation(
    builder: ExpressionBuilder,
    inside: BindingGraph,
) {
    accept(CreationGeneratorVisitor(builder = builder, inside = inside))
}