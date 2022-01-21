package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Component dependency.
 * Every "getter" is exposed as a graph-level binding.
 *
 * @see com.yandex.daggerlite.Component.dependencies
 */
interface ComponentDependencyModel : MayBeInvalid, HasNodeModel {
    val exposedDependencies: Map<NodeModel, FunctionLangModel>
}