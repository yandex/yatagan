package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
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
    val markedGray = hashSetOf<NodeModel>()
    val markedBlack = hashSetOf<NodeModel>()
    val stack = arrayListOf<NodeModel>()

    fun BaseBinding.dependencies(): Collection<NodeDependency> {
        class DependenciesVisitor : BaseBinding.Visitor<Collection<NodeDependency>> {
            override fun visitAlias(alias: AliasBinding) = listOf(NodeDependency(alias.source))
            override fun visitBinding(binding: Binding) = binding.dependencies()
        }
        return accept(DependenciesVisitor())
    }

    fun tryAddToStack(dependency: NodeDependency) {
        val (node, kind) = dependency
        if (!kind.isEager)
            return
        if (node in markedGray) {
            val bindingLoop = stack.dropWhile { it != node }.map { it to graph.resolveRaw(it) }
            validator.reportError(Strings.Errors.`dependency loop`(chain = bindingLoop))
        } else {
            stack += node
        }
    }

    graph.entryPoints.forEach { (_, dependency) -> tryAddToStack(dependency) }
    graph.memberInjectors.forEach { it.membersToInject.forEach { (_, dependency) -> tryAddToStack(dependency) } }

    while(stack.isNotEmpty()) {
        when (val node = stack.last()) {
            in markedBlack -> {
                stack.removeLast()
            }
            in markedGray -> {
                stack.removeLast()
                markedBlack += node
                markedGray -= node
            }
            else -> {
                markedGray += node
                graph.resolveRaw(node).dependencies().forEach(::tryAddToStack)
            }
        }
    }
}