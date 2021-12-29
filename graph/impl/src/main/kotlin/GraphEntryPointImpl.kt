package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.GraphEntryPoint

internal class GraphEntryPointImpl(
    override val owner: BindingGraph,
    private val impl: ComponentModel.EntryPoint,
) : GraphEntryPoint, GraphEntryPointBase() {
    override val getter: FunctionLangModel
        get() = impl.getter

    override val dependency: NodeDependency
        get() = impl.dependency

    override fun toString() = "[entry-point] ${getter.name}"
}