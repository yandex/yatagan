package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.BootstrapListBinding
import com.yandex.daggerlite.graph.ComponentDependencyBinding
import com.yandex.daggerlite.graph.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.graph.ComponentInstanceBinding
import com.yandex.daggerlite.graph.EmptyBinding
import com.yandex.daggerlite.graph.InstanceBinding
import com.yandex.daggerlite.graph.ProvisionBinding
import com.yandex.daggerlite.graph.SubComponentFactoryBinding

internal fun Binding.generateCreation(
    builder: ExpressionBuilder,
    inside: BindingGraph,
) {
    fun componentForBinding(): String {
        return componentForBinding(inside = inside, binding = this)
    }
try {
    when (this) {
        is InstanceBinding -> with(builder) {
            val component = componentForBinding()
            val factory = Generators[owner].factoryGenerator
            +"$component.${factory.fieldNameFor(input)}"
        }
        is ProvisionBinding -> with(builder) {
            generateCall(
                callable = provision,
                arguments = inputs.asIterable(),
                instance = if (requiresModuleInstance) {
                    val component = componentForBinding()
                    "$component.${Generators[owner].factoryGenerator.fieldNameFor(originModule!!)}"
                } else null,
            ) { (node, kind) ->
                inside.resolveBinding(node).generateAccess(builder = this, inside = inside, kind = kind)
            }
        }
        is SubComponentFactoryBinding -> with(builder) {
            +"new %T(".formatCode(Generators[targetGraph].factoryGenerator.implName)
            join(targetGraph.usedParents) { parentGraph ->
                +buildExpression {
                    +componentInstance(inside = inside, graph = parentGraph)
                }
            }
            +")"
        }
        is AlternativesBinding -> with(builder) {
            var exhaustive = false
            for (alternative: NodeModel in alternatives) {
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
                }
            }
            if (!exhaustive) {
                +"null /*empty*/"
            }
        }
        is ComponentInstanceBinding -> with(builder) {
            +componentForBinding()
        }
        is ComponentDependencyBinding -> with(builder) {
            val factory = Generators[owner].factoryGenerator
            +factory.fieldNameFor(input)
        }
        is ComponentDependencyEntryPointBinding -> with(builder) {
            val factory = Generators[owner].factoryGenerator
            +factory.fieldNameFor(input)
            +"."
            +getter.name
            +"()"
        }
        is BootstrapListBinding -> {
            Generators[owner].bootstrapListGenerator.generateCreation(builder, this, inside = inside)
        }
        is EmptyBinding -> throw AssertionError("not handled here")
    }.let { /*exhaustive*/ }
} catch (e: Throwable) {
    throw RuntimeException("While generating creation for $this", e)
}
}