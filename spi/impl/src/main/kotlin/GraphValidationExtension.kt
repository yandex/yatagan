package com.yandex.daggerlite.spi.impl

import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.spi.ValidationPlugin
import com.yandex.daggerlite.spi.ValidationPluginProvider
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.modelRepresentation

class GraphValidationExtension(
    validationPluginProviders: List<ValidationPluginProvider>,
    private val graph: BindingGraph,
) : MayBeInvalid {

    private val validationPlugins: List<ValidationPlugin> = validationPluginProviders.map {
        it.create(graph = graph)
    }
    private val children = graph.children.map { child ->
        GraphValidationExtension(
            validationPluginProviders = validationPluginProviders,
            graph = child,
        )
    }

    override fun validate(validator: Validator) {
        for (validationPlugin in validationPlugins) {
            validator.child(validationPlugin)
        }

        for (child in children) {
            validator.child(child)
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "extension for",
        representation = graph,
    )
}