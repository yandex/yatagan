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
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.EmptyBinding
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.InjectConstructorModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.accept
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.ValidationMessageBuilder
import com.yandex.yatagan.validation.format.buildRichString
import com.yandex.yatagan.validation.format.reportError

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