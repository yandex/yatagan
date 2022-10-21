package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.CollectionTargetKind
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.generator.poetry.CodeBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.Extensible
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType

internal class CollectionBindingGenerator(
    methodsNs: Namespace,
    private val thisGraph: BindingGraph,
) : MultiBindingGeneratorBase<MultiBinding>(
    methodsNs = methodsNs,
    bindings = thisGraph.localBindings.keys.filterIsInstance<MultiBinding>(),
    accessorPrefix = "manyOf",
) {
    override fun buildAccessorCode(builder: CodeBuilder, binding: MultiBinding) = with(builder) {
        when (binding.kind) {
            CollectionTargetKind.List -> {
                +"final %T c = new %T<>(${binding.contributions.size})"
                    .formatCode(binding.target.typeName(), Names.ArrayList)
            }
            CollectionTargetKind.Set -> {
                +"final %T c = new %T<>(${binding.contributions.size})"
                    .formatCode(binding.target.typeName(), Names.HashSet)
            }
        }

        binding.upstream?.let { upstream ->
            +buildExpression {
                +"c.addAll("
                upstream.generateAccess(
                    builder = this,
                    inside = thisGraph,
                    isInsideInnerClass = false,
                )
                +")"
            }
        }
        binding.contributions.forEach { (node: NodeModel, kind: ContributionType) ->
            val nodeBinding = thisGraph.resolveBinding(node)
            generateUnderCondition(
                binding = nodeBinding,
                inside = thisGraph,
                isInsideInnerClass = false,
            ) {
                +buildExpression {
                    when (kind) {
                        ContributionType.Element -> +"c.add("
                        ContributionType.Collection -> +"c.addAll("
                    }
                    nodeBinding.generateAccess(
                        builder = this,
                        inside = thisGraph,
                        isInsideInnerClass = false,
                    )
                    +")"
                }
            }
        }
        +"return c"
    }

    companion object Key : Extensible.Key<CollectionBindingGenerator> {
        override val keyType get() = CollectionBindingGenerator::class.java
    }
}