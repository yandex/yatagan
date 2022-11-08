package com.yandex.yatagan.codegen.impl

import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.DependencyKind

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