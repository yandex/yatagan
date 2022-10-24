package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.EmptyBinding
import com.yandex.daggerlite.core.model.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.model.ComponentFactoryModel
import com.yandex.daggerlite.core.model.ComponentModel
import com.yandex.daggerlite.core.model.HasNodeModel
import com.yandex.daggerlite.core.model.InjectConstructorModel
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.core.model.accept
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.ValidationMessageBuilder
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.reportError

internal data class MissingBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraph,
) : EmptyBinding, BindingDefaultsMixin, ComparableByTargetBindingMixin {

    override fun validate(validator: Validator) {
        target.getSpecificModel().accept(ModelBasedHint(validator))
        // TODO: implement hint about how to provide a binding
        //  - maybe the same differently qualified binding exists
        //  - binding exists in a sibling component hierarchy path
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        color = TextColor.Red
        append("<missing>")
    }

    private inner class ModelBasedHint(
        private val validator: Validator,
    ) : HasNodeModel.Visitor<Unit> {
        private inline fun reportMissingBinding(block: ValidationMessageBuilder.() -> Unit) {
            validator.reportError(Strings.Errors.missingBinding(`for` = target)) {
                if (target.hintIsFrameworkType) {
                    addNote(Strings.Notes.nestedFrameworkType(target))
                } else {
                    block()
                }
            }
        }

        override fun visitDefault() = reportMissingBinding {
            addNote(Strings.Notes.unknownBinding())
        }

        override fun visitInjectConstructor(model: InjectConstructorModel) {
            // This @inject was not used, the problem is in scope then.
            val failedInject = InjectConstructorProvisionBindingImpl(
                impl = model,
                owner = owner,
            )
            validator.reportError(Strings.Errors.noMatchingScopeForBinding(
                binding = failedInject,
                scopes = model.scopes,
            ))
        }

        override fun visitComponent(model: ComponentModel) = reportMissingBinding {
            addNote(Strings.Notes.suspiciousComponentInstanceInject())
        }

        override fun visitAssistedInjectFactory(model: AssistedInjectFactoryModel): Nothing {
            throw AssertionError("Not reached: assisted inject factory can't be missing")
        }

        override fun visitComponentFactory(model: ComponentFactoryModel) = reportMissingBinding {
            addNote(
                if (!model.createdComponent.isRoot) {
                    Strings.Notes.subcomponentFactoryInjectionHint(
                        factory = model,
                        component = model.createdComponent,
                        owner = owner,
                    )
                } else {
                    Strings.Notes.suspiciousRootComponentFactoryInject(factoryLike = target)
                }
            )
        }
    }
}