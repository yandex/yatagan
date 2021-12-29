package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.isOptional
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.reportError

internal abstract class GraphEntryPointBase : MayBeInvalid {
    abstract val owner: BindingGraph
    abstract val dependency: NodeDependency

    override fun validate(validator: Validator) {
        val (node, kind) = dependency
        val resolved = owner.resolveBinding(node)
        if (!kind.isOptional) {
            if (resolved.conditionScope /* no component scope */ !in owner.conditionScope) {
                validator.reportError(Strings.Errors.`incompatible condition scope for entry-point`(
                    aCondition = resolved.conditionScope, bCondition = owner.conditionScope,
                    binding = resolved, component = owner,
                ))
            }
        }
        validator.child(resolved)
    }
}