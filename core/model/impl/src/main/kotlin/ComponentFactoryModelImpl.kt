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

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.core.model.ClassBackedModel
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.BuilderInputModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.FactoryInputModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.allInputs
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.lang.isKotlinObject
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError
import com.yandex.yatagan.validation.format.reportWarning
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class ComponentFactoryModelImpl private constructor(
    private val factoryDeclaration: TypeDeclaration,
) : ComponentFactoryModel {

    override val createdComponent: ComponentModel by lazy {
        ComponentModelImpl(factoryDeclaration.enclosingType ?: LangModelFactory.errorType.declaration)
    }

    override val factoryMethod = factoryDeclaration.methods.find {
        it.isAbstract && it.returnType == createdComponent.type
    }

    override val type: Type
        get() = factoryDeclaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = factoryDeclaration.asType(),
        forQualifier = null,
    )

    override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
        return visitor.visitComponentFactory(this)
    }

    override val builderInputs: Collection<BuilderInputModel> = factoryDeclaration.methods.filter {
        it.isAbstract && it != factoryMethod && it.parameters.count() == 1
    }.map { method ->
        object : BuilderInputModel {
            override val payload: InputPayload by lazy(PUBLICATION) {
                InputPayload(
                    param = method.parameters.first(),
                    hasBindsInstance = { method.getAnnotation(BuiltinAnnotation.BindsInstance) != null },
                )
            }
            override val name get() = method.name
            override val builderSetter get() = method

            override fun validate(validator: Validator) {
                validator.child(payload)
                if (method.parameters.first().getAnnotation(BuiltinAnnotation.BindsInstance) != null) {
                    validator.reportWarning(Strings.Warnings.ignoredBindsInstance())
                }
                if (!method.returnType.isVoid && !method.returnType.isAssignableFrom(factoryDeclaration.asType())) {
                    validator.reportError(Strings.Errors.invalidBuilderSetterReturn(
                        creatorType = factoryDeclaration.asType(),
                    ))
                }
            }

            override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
                modelClassName = "creator-setter",
                representation = {
                    append("$name(")
                    if (childContext != null) {
                        appendChildContextReference(reference = method.parameters.first())
                    } else {
                        append(method.parameters.first())
                    }
                    append("): ")
                    append(method.returnType)
                }
            )
        }
    }.toList()

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
                        append("${factoryMethod.name}(")
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

    private inline fun InputPayload(
        param: Parameter,
        hasBindsInstance: () -> Boolean,
    ): InputPayload {
        val declaration = param.type.declaration
        return when {
            ModuleModelImpl.canRepresent(declaration) ->
                InputPayloadModuleImpl(module = ModuleModelImpl(declaration))

            hasBindsInstance() ->
                InputPayloadInstanceImpl(node = NodeModelImpl(param.type, forQualifier = param))

            else -> InputPayloadDependencyImpl(dependency = ComponentDependencyModelImpl(declaration.asType()))
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-creator",
        representation = factoryDeclaration,
    )

    override fun validate(validator: Validator) {
        validator.inline(asNode())
        for (input in allInputs) {
            validator.child(input)
        }

        if (factoryDeclaration.kind != TypeDeclarationKind.Interface) {
            validator.reportError(Strings.Errors.nonInterfaceCreator())
        }
        val factory = factoryMethod
        if (factory == null) {
            validator.reportError(Strings.Errors.missingCreatingMethod())
        }

        for (method in factoryDeclaration.methods) {
            if (method == factoryMethod || method.parameters.count() == 1 || !method.isAbstract)
                continue
            validator.reportError(Strings.Errors.unknownMethodInCreator(method = method))
        }

        // TODO: check for duplicates in modules, dependencies.

        // Check for missing component dependencies
        val providedDependencies = allInputs
            .map { it.payload }
            .filterIsInstance<InputPayload.Dependency>()
            .map { it.dependency }
            .toSet()
        for (missingDependency in createdComponent.dependencies - providedDependencies) {
            validator.reportError(Strings.Errors.missingComponentDependency(missing = missingDependency))
        }

        // Check for missing modules, that require instance and not trivially constructable
        val providedModules = allInputs
            .map { it.payload }
            .filterIsInstance<InputPayload.Module>()
            .map { it.module }
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

    private inner class InputPayloadModuleImpl(
        override val module: ModuleModel,
    ) : InputPayload.Module, ClassBackedModel by module {
        override fun validate(validator: Validator) {
            if (!module.requiresInstance ||
                module !in createdComponent.modules
            ) {
                validator.reportError(Strings.Errors.extraModule()) {
                    if (module !in createdComponent.modules) {
                        addNote(Strings.Notes.undeclaredModulePresent())
                    } else {
                        assert(!module.requiresInstance)
                        if (module.type.declaration.isKotlinObject) {
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
            representation = module,
        )
    }

    private class InputPayloadInstanceImpl(
        override val node: NodeModel,
    ) : InputPayload.Instance, ClassBackedModel by node {
        override fun validate(validator: Validator) {
            validator.inline(node)
        }

        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "input",
            representation = node,
        )
    }

    private inner class InputPayloadDependencyImpl(
        override val dependency: ComponentDependencyModel,
    ) : InputPayload.Dependency, ClassBackedModel by dependency {
        override fun validate(validator: Validator) {
            if (dependency !in createdComponent.dependencies) {
                validator.reportError(Strings.Errors.extraComponentDependency()) {
                    addNote(Strings.Notes.adviceBindInstanceForUnknownInput())
                    addNote(Strings.Notes.adviceComponentDependencyForUnknownInput())
                }
            }
        }

        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "input",
            representation = dependency,
        )
    }

    companion object Factory : ObjectCache<TypeDeclaration, ComponentFactoryModelImpl>() {
        operator fun invoke(
            factoryDeclaration: TypeDeclaration,
        ) = createCached(factoryDeclaration) {
            ComponentFactoryModelImpl(
                factoryDeclaration = it,
            )
        }

        fun canRepresent(declaration: TypeDeclaration): Boolean {
            return declaration.getAnnotation(BuiltinAnnotation.Component.Builder) != null
        }
    }
}
