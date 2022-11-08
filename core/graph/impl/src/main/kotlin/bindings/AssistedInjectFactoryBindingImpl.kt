package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.bindingModelRepresentation

internal class AssistedInjectFactoryBindingImpl(
    override val owner: BindingGraph,
    override val model: AssistedInjectFactoryModel,
) : AssistedInjectFactoryBinding, BindingDefaultsMixin, ComparableByTargetBindingMixin {
    override val target: NodeModel
        get() = model.asNode()

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitAssistedInjectFactory(this)
    }

    override val dependencies by lazy(LazyThreadSafetyMode.PUBLICATION) {
        model.assistedConstructorParameters
            .asSequence()
            .filterIsInstance<AssistedInjectFactoryModel.Parameter.Injected>()
            .map { it.dependency }
            .memoize()
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.inline(model)
    }

    override val checkDependenciesConditionScope get() = true

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "assisted-factory",
        childContext = childContext,
        representation = {
            append(model.type)
            append("::")
            if (model.factoryMethod != null) {
                append(model.factoryMethod!!.name)
                append("(): ")
                if (model.assistedInjectConstructor != null) {
                    append(model.assistedInjectConstructor!!.constructee)
                } else {
                    appendRichString {
                        color = TextColor.Red
                        append("<invalid-target>")
                    }
                }
            } else {
                appendRichString {
                    color = TextColor.Red
                    append("<missing-factory-method>")
                }
            }
        },
    )
}