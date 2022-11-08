package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.base.ifOrElseNull
import com.yandex.yatagan.base.traverseDepthFirstWithPath
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.component1
import com.yandex.yatagan.core.graph.component2
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.core.model.isEager
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.reportError
import kotlin.collections.component1
import kotlin.collections.component2

internal fun validateNoLoops(graph: BindingGraph, validator: Validator) {
    traverseDepthFirstWithPath(
        roots = buildList<BaseBinding> {
            graph.entryPoints.forEach { (_, dependency) ->
                add(graph.resolveBindingRaw(dependency.node))
            }
            graph.memberInjectors.forEach {
                it.membersToInject.forEach { (_, dependency) ->
                    add(graph.resolveBindingRaw(dependency.node))
                }
            }
        },
        childrenOf = { binding ->
            class DependenciesVisitor : BaseBinding.Visitor<Sequence<NodeDependency>> {
                override fun visitAlias(alias: AliasBinding) = sequenceOf(alias.source)
                override fun visitBinding(binding: Binding) =
                    binding.dependencies + binding.nonStaticConditionProviders
            }
            binding.accept(DependenciesVisitor()).mapNotNull { (node, kind) ->
                ifOrElseNull(kind.isEager) { binding.owner.resolveBindingRaw(node) }
            }.asIterable()
        },
        onLoop = { bindingLoop ->
            validator.reportError(Strings.Errors.dependencyLoop(chain = bindingLoop.toList()))
        }
    )
}