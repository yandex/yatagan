package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.AliasBinding
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.EmptyBinding
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.reportError

internal class AliasLoopStubBinding(
    override val owner: BindingGraph,
    override val target: NodeModel,
    private val aliasLoop: Collection<AliasBinding>,
) : EmptyBinding, BindingDefaultsMixin, ComparableByTargetBindingMixin {
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