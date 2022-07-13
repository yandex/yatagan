package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.component1
import com.yandex.daggerlite.core.component2
import com.yandex.daggerlite.core.isEager
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.component1
import com.yandex.daggerlite.graph.component2
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.reportError

internal fun validateNoLoops(graph: BindingGraphImpl, validator: Validator) {
    val markedGray = hashSetOf<BaseBinding>()
    val markedBlack = hashSetOf<BaseBinding>()
    val stack = arrayListOf<BaseBinding>()

    fun BaseBinding.dependencies(): Sequence<NodeDependency> {
        class DependenciesVisitor : BaseBinding.Visitor<Sequence<NodeDependency>> {
            override fun visitAlias(alias: AliasBinding) = sequenceOf(alias.source)
            override fun visitBinding(binding: Binding) = binding.dependencies
        }
        return accept(DependenciesVisitor())
    }

    fun tryAddToStack(dependency: NodeDependency, context: BindingGraphImpl) {
        val (node, kind) = dependency
        if (!kind.isEager)
            return
        val binding = context.resolveRaw(node)
        if (binding in markedGray) {
            val bindingLoop = stack.dropWhile { it != binding }.map { it.target to it }
            validator.reportError(Strings.Errors.dependencyLoop(chain = bindingLoop))
        } else {
            stack += context.resolveRaw(node)
        }
    }

    graph.entryPoints.forEach { (_, dependency) ->
        tryAddToStack(dependency, context = graph)
    }
    graph.memberInjectors.forEach { it.membersToInject.forEach { (_, dependency) ->
        tryAddToStack(dependency, context = graph) }
    }

    while (stack.isNotEmpty()) {
        when (val binding = stack.last()) {
            in markedBlack -> {
                stack.removeLast()
            }

            in markedGray -> {
                stack.removeLast()
                markedBlack += binding
                markedGray -= binding
            }

            else -> {
                markedGray += binding
                binding.dependencies().forEach {
                    tryAddToStack(it, context = binding.owner as BindingGraphImpl)
                }
            }
        }
    }
}