package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.EmptyBinding
import com.yandex.daggerlite.core.model.ModuleHostedBindingModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.reportError

internal class SelfDependentInvalidBinding(
    override val impl: ModuleHostedBindingModel,
    override val owner: BindingGraph,
) : EmptyBinding, BindingDefaultsMixin, ModuleHostedBindingMixin() {
    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        // Always invalid
        validator.reportError(Strings.Errors.selfDependentBinding())
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        color = TextColor.Red
        append("<invalid> ")
        append(impl)
    }
}