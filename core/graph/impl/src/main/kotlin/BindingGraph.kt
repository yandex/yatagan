package com.yandex.daggerlite.core.graph.impl

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.model.ComponentModel

/**
 * Creates [BindingGraph] instance given the root component.
 */
fun BindingGraph(root: ComponentModel): BindingGraph {
    require(root.isRoot) { "Not reached: can't use non-root component as a root of a binding graph" }
    return BindingGraphImpl(
        component = root,
    )
}