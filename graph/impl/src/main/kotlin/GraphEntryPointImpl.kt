package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.GraphEntryPoint

internal class GraphEntryPointImpl(
    override val graph: BindingGraphImpl,
    private val impl: ComponentModel.EntryPoint,
) : GraphEntryPoint, GraphEntryPointBase() {
    override val getter: FunctionLangModel
        get() = impl.getter

    override val dependency: NodeDependency
        get() = impl.dependency

    override fun toString() = impl.toString()
}