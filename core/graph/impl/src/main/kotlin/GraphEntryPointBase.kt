package com.yandex.daggerlite.core.graph.impl

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.component1
import com.yandex.daggerlite.core.model.component2
import com.yandex.daggerlite.core.model.isOptional
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.reportError

internal abstract class GraphEntryPointBase : MayBeInvalid {
    abstract val graph: BindingGraph
    abstract val dependency: NodeDependency

    override fun validate(validator: Validator) {
        val (node, kind) = dependency
        val resolved = graph.resolveBinding(node)
        if (!kind.isOptional) {
            if (resolved.conditionScope /* no component scope */ !in graph.conditionScope) {
                validator.reportError(Strings.Errors.incompatibleConditionEntryPoint(
                    aCondition = resolved.conditionScope, bCondition = graph.conditionScope,
                    binding = resolved, component = graph,
                ))
            }
        }
        validator.child(graph.resolveBindingRaw(node))
    }
}