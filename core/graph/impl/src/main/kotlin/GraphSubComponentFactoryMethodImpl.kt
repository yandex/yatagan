/*
 * Copyright 2023 Yandex LLC
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

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.GraphSubComponentFactoryMethod
import com.yandex.yatagan.core.model.SubComponentFactoryMethodModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.reportError

internal class GraphSubComponentFactoryMethodImpl(
    private val owner: BindingGraphImpl,
    override val model: SubComponentFactoryMethodModel,
) : GraphSubComponentFactoryMethod {
    override val createdGraph: BindingGraph?
        get() = owner.children.find { it.model == model.createdComponent }

    override fun validate(validator: Validator) {
        val createdGraph = createdGraph
        if (createdGraph == null) {
            validator.reportError(Strings.Errors.componentLoop())
            return
        }

        if (createdGraph.conditionScope !in owner.conditionScope) {
            validator.reportError(Strings.Errors.incompatibleConditionChildComponentFactory(
                aCondition = createdGraph.conditionScope,
                bCondition = owner.conditionScope,
                factory = this,
            ))
        }
    }

    override fun toString(childContext: MayBeInvalid?) = model.toString(null)
}