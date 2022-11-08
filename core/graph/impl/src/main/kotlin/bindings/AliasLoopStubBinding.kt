package com.yandex.yatagan.core.graph.impl.bindings

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