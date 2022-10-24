package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.AliasBinding
import com.yandex.daggerlite.core.graph.bindings.BaseBinding
import com.yandex.daggerlite.core.model.BindsBindingModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.bindingModelRepresentation
import com.yandex.daggerlite.validation.format.reportMandatoryWarning

internal class AliasBindingImpl(
    override val impl: BindsBindingModel,
    override val owner: BindingGraph,
) : AliasBinding, ModuleHostedBindingMixin() {
    init {
        assert(impl.sources.count() == 1) {
            "Not reached: sources count must be equal to 1"
        }
    }

    override val source get() = impl.sources.single()

    override fun equals(other: Any?): Boolean {
        return this === other || (other is AliasBinding &&
                source == other.source && target == other.target &&
                owner == other.owner)
    }

    override fun hashCode(): Int {
        var result = target.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + owner.hashCode()
        return result
    }

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "alias",
        childContext = childContext,
        representation = { append(impl.originModule.type).append("::").append(impl.method.name) },
        ellipsisStatistics = { _, dependencies ->
            if (dependencies.isNotEmpty()) append(dependencies.first())  // Always include alias source
        },
    )

    override fun validate(validator: Validator) {
        validator.child(owner.resolveBindingRaw(source))
        if (impl.scopes.isNotEmpty()) {
            validator.reportMandatoryWarning(Strings.Warnings.scopeRebindIsForbidden()) {
                addNote(Strings.Notes.infoOnScopeRebind())
            }
        }
    }

    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitAlias(this)
    }
}