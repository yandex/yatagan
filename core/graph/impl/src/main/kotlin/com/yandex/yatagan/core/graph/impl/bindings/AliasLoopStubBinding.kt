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

import com.yandex.yatagan.base.ExtensibleImpl
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.EmptyBinding
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.buildRichString
import com.yandex.yatagan.validation.format.reportError

internal class AliasLoopStubBinding(
    override val owner: BindingGraph,
    override val target: NodeModel,
    private val aliasLoop: Collection<AliasBinding>,
) : EmptyBinding, BindingDefaultsMixin, ComparableByTargetBindingMixin, ExtensibleImpl() {
    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.reportError(Strings.Errors.dependencyLoop(aliasLoop.toList()))
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        color = TextColor.Red
        append("<alias-loop> ")
        append(aliasLoop.first())
    }
}