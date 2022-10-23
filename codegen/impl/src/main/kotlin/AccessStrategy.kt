package com.yandex.daggerlite.codegen.impl

import com.yandex.daggerlite.codegen.poetry.ExpressionBuilder
import com.yandex.daggerlite.codegen.poetry.TypeSpecBuilder
import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.model.DependencyKind

internal interface AccessStrategy {
    fun generateInComponent(builder: TypeSpecBuilder) {
        // nothing by default
    }

    /**
     * Generates access expression [inside] the given component.
     */
    fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean,
    )
}