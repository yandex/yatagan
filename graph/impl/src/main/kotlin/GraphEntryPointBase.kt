package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.isOptional
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.reportError

internal abstract class GraphEntryPointBase : MayBeInvalid {
    abstract val graph: BindingGraph
    abstract val dependency: NodeDependency

    override fun validate(validator: Validator) {
        val (node, kind) = dependency
        val resolved = graph.resolveBinding(node)
        if (!kind.isOptional) {
            if (resolved.conditionScope /* no component scope */ !in graph.conditionScope) {
                validator.reportError(Strings.Errors.incompatibleConditionEntyPoint(
                    aCondition = resolved.conditionScope, bCondition = graph.conditionScope,
                    binding = resolved, component = graph,
                ))
            }
        }
        validator.child(resolved)
    }
}