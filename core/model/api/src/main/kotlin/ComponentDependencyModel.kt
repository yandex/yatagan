package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Component dependency.
 * Every "getter" is exposed as a graph-level binding.
 *
 * @see com.yandex.daggerlite.Component.dependencies
 */
interface ComponentDependencyModel : MayBeInvalid, ClassBackedModel {
    val exposedDependencies: Map<NodeModel, FunctionLangModel>

    fun asNode(): NodeModel
}