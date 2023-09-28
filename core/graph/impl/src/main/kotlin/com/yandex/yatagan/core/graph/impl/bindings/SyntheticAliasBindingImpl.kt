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
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator

internal class SyntheticAliasBindingImpl(
    val sourceBinding: Binding,
    override val target: NodeModel,
) : AliasBinding {
    override val source: NodeModel
        get() = sourceBinding.target

    override val owner: BindingGraph
        get() = sourceBinding.owner

    override val originModule: ModuleModel? get() = null
    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitAlias(this)
    }

    override fun validate(validator: Validator) {
        validator.inline(owner.resolveBindingRaw(source))
    }

    override fun toString(childContext: MayBeInvalid?): CharSequence {
        // Pass-through
        return sourceBinding.toString(childContext)
    }

    override fun compareTo(other: BaseBinding): Int {
        // Pass-through
        return sourceBinding.compareTo(other)
    }
}