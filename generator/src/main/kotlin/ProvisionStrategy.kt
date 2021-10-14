package com.yandex.dagger3.generator

import com.yandex.dagger3.generator.poetry.ExpressionBuilder
import com.yandex.dagger3.generator.poetry.TypeSpecBuilder

internal interface ProvisionStrategy {
    fun generateInComponent(builder: TypeSpecBuilder) {
        // nothing by default
    }

    /**
     * Generates access expression [inside] the given component.
     */
    fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind)
}