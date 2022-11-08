package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.core.model.isOptional
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.reportError

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