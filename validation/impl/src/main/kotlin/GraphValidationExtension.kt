/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.validation.impl

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.spi.ValidationPlugin
import com.yandex.yatagan.validation.spi.ValidationPluginProvider

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
        modelClassName = "external plugins for",
        representation = graph,
    )
}