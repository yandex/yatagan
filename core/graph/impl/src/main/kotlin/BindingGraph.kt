package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.ComponentModel

/**
 * Creates [BindingGraph] instance given the root component.
 */
fun BindingGraph(root: ComponentModel): BindingGraph {
    require(root.isRoot) { "Not reached: can't use non-root component as a root of a binding graph" }
    return BindingGraphImpl(
        component = root,
    )
}