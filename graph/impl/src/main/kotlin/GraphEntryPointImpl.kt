package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.isOptional
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.GraphEntryPoint
import com.yandex.daggerlite.validation.Validator

internal class GraphEntryPointImpl(
    private val owner: BindingGraph,
    private val impl: ComponentModel.EntryPoint,
) : GraphEntryPoint {
    override val getter: FunctionLangModel
        get() = impl.getter

    override val dependency: NodeDependency
        get() = impl.dependency

    override fun validate(validator: Validator) {
        val (node, kind) = dependency
        val resolved = owner.resolveBinding(node)
        if (!kind.isOptional) {
            // TODO: validate that this entry point is unscoped according to this component.
        }
        validator.child(resolved)
    }

    override fun toString() = "[entry-point] ${getter.name}"
}