package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ClassBackedModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentFactoryModel.BuilderInputModel
import com.yandex.daggerlite.core.ComponentFactoryModel.FactoryInputModel
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.HasNodeModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationKind
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError
import com.yandex.daggerlite.validation.format.reportWarning
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class ComponentFactoryModelImpl private constructor(
    private val factoryDeclaration: TypeDeclarationLangModel,
) : ComponentFactoryModel {

    override val createdComponent: ComponentModel by lazy {
        ComponentModelImpl(factoryDeclaration.enclosingType ?: LangModelFactory.errorType.declaration)
    }

    override val factoryMethod = factoryDeclaration.functions.find {
        it.isAbstract && it.returnType == createdComponent.type
    }

    override val type: TypeLangModel
        get() = factoryDeclaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = factoryDeclaration.asType(),
        forQualifier = null,
    )

    override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
        return visitor.visitComponentFactory(this)
    }

    override val builderInputs: Collection<BuilderInputModel> = factoryDeclaration.functions.filter {
        it.isAbstract && it != factoryMethod && it.parameters.count() == 1
    }.map { method ->
        object : BuilderInputModel {
            override val payload: InputPayload by lazy(PUBLICATION) {
                InputPayload(
                    param = method.parameters.first(),
                    forBindsInstance = method,
                )
            }
            override val name get() = method.name
            override val builderSetter get() = method

            override fun validate(validator: Validator) {
                validator.child(payload)
                if (method.parameters.first().isAnnotatedWith<BindsInstance>()) {
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
                    InputPayload(param = param)
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

    private fun InputPayload(
        param: ParameterLangModel,
        forBindsInstance: AnnotatedLangModel = param,
    ): InputPayload {
        val declaration = param.type.declaration
        return when {
            ModuleModelImpl.canRepresent(declaration) ->
                InputPayloadModuleImpl(module = ModuleModelImpl(declaration))

            forBindsInstance.isAnnotatedWith<BindsInstance>() ->
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

        for (function in factoryDeclaration.functions) {
            if (function == factoryMethod || function.parameters.count() == 1 || !function.isAbstract)
                continue
            validator.reportError(Strings.Errors.unknownMethodInCreator(method = function))
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
                        addNote(Strings.Notes.moduleDoesNotRequireInstance())
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

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ComponentFactoryModelImpl>() {
        operator fun invoke(
            factoryDeclaration: TypeDeclarationLangModel,
        ) = createCached(factoryDeclaration) {
            ComponentFactoryModelImpl(
                factoryDeclaration = it,
            )
        }

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.isAnnotatedWith<Component.Builder>()
        }
    }
}
