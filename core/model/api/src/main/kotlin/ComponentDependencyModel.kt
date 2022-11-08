package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Component dependency.
 * Every "getter" is exposed as a graph-level binding.
 *
 * @see com.yandex.yatagan.Component.dependencies
 */
interface ComponentDependencyModel : MayBeInvalid, ClassBackedModel {
    val exposedDependencies: Map<NodeModel, Method>

    fun asNode(): NodeModel
}