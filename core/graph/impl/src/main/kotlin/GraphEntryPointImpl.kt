package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.GraphEntryPoint
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.modelRepresentation

internal class GraphEntryPointImpl(
    override val graph: BindingGraphImpl,
    private val impl: ComponentModel.EntryPoint,
) : GraphEntryPoint, GraphEntryPointBase() {
    override val getter: FunctionLangModel
        get() = impl.getter

    override val dependency: NodeDependency
        get() = impl.dependency

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "entry-point",
        representation = {
            append("${impl.getter.name}: ")
            if (childContext != null) {
                appendChildContextReference(reference = dependency)
            } else {
                append(dependency)
            }
        },
    )
}