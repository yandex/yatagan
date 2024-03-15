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

package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.base.ExtensibleImpl
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.modelRepresentation

internal class ComponentDependencyEntryPointBindingImpl(
    override val owner: BindingGraph,
    override val dependency: ComponentDependencyModel,
    override val getter: Method,
    override val target: NodeModel,
) : ComponentDependencyEntryPointBinding, BindingDefaultsMixin,
    ComparableBindingMixin<ComponentDependencyEntryPointBindingImpl>, ExtensibleImpl() {

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentDependencyEntryPoint(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-dependency-getter",
        representation = getter,
    )

    override fun compareTo(other: ComponentDependencyEntryPointBindingImpl): Int {
        return getter.compareTo(other.getter)
    }
}