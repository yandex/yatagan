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

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.FactoryInputModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.allInputs
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.isKotlinObject
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal abstract class ComponentFactoryModelBase : ComponentFactoryModel {

    override val factoryInputs: Collection<FactoryInputModel> by lazy {
        factoryMethod?.parameters?.map { param ->
            object : FactoryInputModel {
                override val payload: InputPayload by lazy(PUBLICATION) {
                    InputPayload(
                        param = param,
                        hasBindsInstance = { param.getAnnotation(BuiltinAnnotation.BindsInstance) != null },
                    )
                }
                override val name get() = param.name

                override fun validate(validator: Validator) {
                    validator.child(payload)
                }

                override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
                    modelClassName = "creator-method-parameter",
                    representation = {
                        append("${factoryMethod?.name}(")
                        if (childContext == payload) {
                            append(".., ")
                            appendChildContextReference(reference = param)
                            append(", ..)")
                        } else {
                            append("...)")
                        }
                    }
                )
            }
        }?.toList() ?: emptyList()
    }

    protected inline fun InputPayload(
        param: Parameter,
        hasBindsInstance: () -> Boolean,
    ): InputPayload {
        val declaration = param.type.declaration
        return when {
            ModuleModelImpl.canRepresent(declaration) ->
                InputPayloadModuleImpl(model = ModuleModelImpl(declaration))

            hasBindsInstance() ->
                InputPayloadInstanceImpl(model = NodeModelImpl(param.type, forQualifier = param))

            else -> InputPayloadDependencyImpl(model = ComponentDependencyModelImpl(declaration.asType()))
        }
    }

    override fun validate(validator: Validator) {
        for (input in allInputs) {
            validator.child(input)
        }

        val factory = factoryMethod
        if (factory == null) {
            validator.reportError(Strings.Errors.missingCreatingMethod())
        }

        // TODO: check for duplicates in modules, dependencies.

        // Check for missing component dependencies
        val providedDependencies = allInputs
            .map { it.payload }
            .filterIsInstance<InputPayload.Dependency>()
            .map { it.model }
            .toSet()
        for (missingDependency in createdComponent.dependencies - providedDependencies) {
            validator.reportError(Strings.Errors.missingComponentDependency(missing = missingDependency))
        }

        // Check for missing modules, that require instance and not trivially constructable
        val providedModules = allInputs
            .map { it.payload }
            .filterIsInstance<InputPayload.Module>()
            .map { it.model }
            .toSet()
        val allModulesRequiresInstance = createdComponent.modules.asSequence()
            .filter(ModuleModel::requiresInstance).toMutableSet()
        for (missingModule in (allModulesRequiresInstance - providedModules).filter { !it.isTriviallyConstructable }) {
            validator.reportError(Strings.Errors.missingModule(missing = missingModule)) {
                missingModule.type.declaration.constructors
                    .filter { it.parameters.none() }
                    .forEach {
                        addNote(Strings.Notes.inaccessibleAutoConstructorForMissingModule(constructor = it))
                    }
            }
        }
    }

    protected inner class InputPayloadModuleImpl(
        override val model: ModuleModel,
    ) : InputPayload.Module {
        override fun validate(validator: Validator) {
            if (!model.requiresInstance ||
                model !in createdComponent.modules
            ) {
                validator.reportError(Strings.Errors.extraModule()) {
                    if (model !in createdComponent.modules) {
                        addNote(Strings.Notes.undeclaredModulePresent())
                    } else {
                        assert(!model.requiresInstance)
                        if (model.type.declaration.isKotlinObject) {
                            addNote(Strings.Notes.objectModuleInBuilder())
                        } else {
                            addNote(Strings.Notes.moduleDoesNotRequireInstance())
                        }
                    }
                }
            }
        }

        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "input",
            representation = model,
        )
    }

    protected class InputPayloadInstanceImpl(
        override val model: NodeModel,
    ) : InputPayload.Instance {
        override fun validate(validator: Validator) {
            validator.inline(model)
        }

        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "input",
            representation = model,
        )
    }

    protected inner class InputPayloadDependencyImpl(
        override val model: ComponentDependencyModel,
    ) : InputPayload.Dependency {
        override fun validate(validator: Validator) {
            // dependency itself is not validated here; it's validated as a ComponentModel's child (if present there).
            if (model !in createdComponent.dependencies) {
                validator.reportError(Strings.Errors.extraComponentDependency()) {
                    addNote(Strings.Notes.adviceBindInstanceForUnknownInput())
                    addNote(Strings.Notes.adviceComponentDependencyForUnknownInput())
                }
            }
        }

        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "input",
            representation = model,
        )
    }
}
