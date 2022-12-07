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
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation

internal class GraphEntryPointImpl(
    override val graph: BindingGraphImpl,
    private val impl: ComponentModel.EntryPoint,
) : GraphEntryPoint, GraphEntryPointBase() {
    override val getter: Method
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