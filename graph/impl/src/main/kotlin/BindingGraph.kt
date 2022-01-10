package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.graph.BindingGraph

/**
 * Creates [BindingGraph] instance given the root component.
 */
fun BindingGraph(root: ComponentModel): BindingGraph {
    require(root.isRoot) { "Not reached: can't use non-root component as a root of a binding graph" }
    return BindingGraphImpl(
        component = root,
        conditionScope = com.yandex.daggerlite.graph.ConditionScope.Unscoped,
    )
}