package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.generator.poetry.CodeBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.Extensible
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType

internal class MultiBindingGenerator(
    methodsNs: Namespace,
    private val thisGraph: BindingGraph,
) : MultiBindingGeneratorBase<MultiBinding>(
    methodsNs = methodsNs,
    bindings = thisGraph.localBindings.keys.filterIsInstance<MultiBinding>(),
    accessorPrefix = "listOf",
) {
    override fun buildAccessorCode(builder: CodeBuilder, binding: MultiBinding) = with(builder) {
        +"final %T list = new %T<>(${binding.contributions.size})"
            .formatCode(binding.target.typeName(), Names.ArrayList)
        binding.contributions.forEach { (node: NodeModel, kind: ContributionType) ->
            val nodeBinding = thisGraph.resolveBinding(node)
            generateUnderCondition(
                binding = nodeBinding,
                inside = thisGraph,
            ) {
                +buildExpression {
                    when (kind) {
                        ContributionType.Element -> +"list.add("
                        ContributionType.Collection -> +"list.addAll("
                    }
                    nodeBinding.generateAccess(builder = this, inside = thisGraph)
                    +")"
                }
            }
        }
        +"return list"
    }

    companion object Key : Extensible.Key<MultiBindingGenerator> {
        override val keyType get() = MultiBindingGenerator::class.java
    }
}