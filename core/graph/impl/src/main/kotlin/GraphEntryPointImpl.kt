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

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.GraphEntryPoint
import com.yandex.yatagan.core.model.ComponentEntryPoint
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.accept
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportWarning

internal class GraphEntryPointImpl(
    override val graph: BindingGraphImpl,
    private val impl: ComponentEntryPoint,
) : GraphEntryPoint, GraphEntryPointBase() {
    override val getter: Method
        get() = impl.getter

    override val dependency: NodeDependency
        get() = impl.dependency

    override fun validate(validator: Validator) {
        super.validate(validator)
        validateChildComponentInclusion(validator)
    }

    private fun validateChildComponentInclusion(validator: Validator) {
        val detector = object : HasNodeModel.Visitor<ComponentModel?> {
            override fun visitOther() = null
            override fun visitComponent(model: ComponentModel) = model
        }
        val component = dependency.node.getSpecificModel().accept(detector)
        if (component == null || component !in graph.childrenModels)
            return

        component.factory?.let {
            validator.reportWarning(Strings.Warnings.subcomponentViaEntryPointWithCreator(
                subcomponent = component,
                creator = it,
            ))
        }
    }

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