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

package com.yandex.yatagan.core.graph

import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyBinding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.graph.bindings.ComponentInstanceBinding
import com.yandex.yatagan.core.graph.bindings.EmptyBinding
import com.yandex.yatagan.core.graph.bindings.InstanceBinding
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding
import com.yandex.yatagan.core.graph.bindings.SubComponentBinding
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Method

/**
 * Discards negation from the literal.
 *
 * @return `!this` if negated, `this` otherwise.
 */
public fun ConditionModel.normalized(): ConditionModel {
    return if (negated) !this else this
}

public operator fun GraphEntryPoint.component1(): Method = getter

public operator fun GraphEntryPoint.component2(): NodeDependency = dependency

public abstract class BindingVisitorAdapter<R> : Binding.Visitor<R> {
    public abstract fun visitDefault(): R
    override fun visitProvision(binding: ProvisionBinding): R = visitDefault()
    override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding): R = visitDefault()
    override fun visitInstance(binding: InstanceBinding): R = visitDefault()
    override fun visitAlternatives(binding: AlternativesBinding): R = visitDefault()
    override fun visitSubComponent(binding: SubComponentBinding): R = visitDefault()
    override fun visitComponentDependency(binding: ComponentDependencyBinding): R = visitDefault()
    override fun visitComponentInstance(binding: ComponentInstanceBinding): R = visitDefault()
    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding): R = visitDefault()
    override fun visitMulti(binding: MultiBinding): R = visitDefault()
    override fun visitMap(binding: MapBinding): R = visitDefault()
    override fun visitEmpty(binding: EmptyBinding): R = visitDefault()
}
