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
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Inline
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.reportError
import com.yandex.daggerlite.validation.impl.reportWarning
import kotlin.LazyThreadSafetyMode.NONE

internal class ComponentFactoryModelImpl private constructor(
    private val factoryDeclaration: TypeDeclarationLangModel,
) : ComponentFactoryModel {

    override val createdComponent: ComponentModel by lazy(NONE) {
        ComponentModelImpl(factoryDeclaration.enclosingType ?: LangModelFactory.errorType.declaration)
    }

    override val factoryMethod = factoryDeclaration.allPublicFunctions.find {
        it.isAbstract && it.returnType == createdComponent.type
    }

    override val type: TypeLangModel
        get() = factoryDeclaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = factoryDeclaration.asType(),
        forQualifier = null,
    )

    override val builderInputs: Collection<BuilderInputModel> = factoryDeclaration.allPublicFunctions.filter {
        it.isAbstract && it != factoryMethod && it.parameters.count() == 1
    }.map { method ->
        object : BuilderInputModel {
            override val payload: InputPayload by lazy(NONE) {
                InputPayload(
                    param = method.parameters.first(),
                    forBindsInstance = method,
                )
            }
            override val name get() = method.name
            override val builderSetter get() = method

            override fun validate(validator: Validator) {
                validator.child(payload, kind = Inline)
                if (method.parameters.first().isAnnotatedWith<BindsInstance>()) {
                    validator.reportWarning(Strings.Warnings.`@BindsInstance on builder method's parameter`())
                }
                if (!method.returnType.isVoid && !method.returnType.isAssignableFrom(factoryDeclaration.asType())) {
                    validator.reportError(Errors.`invalid builder setter return type`(
                        creatorType = factoryDeclaration.asType(),
                    ))
                }
            }

            override fun toString() = "[setter] ${builderSetter.name}($payload)"
        }
    }.toList()

    override val factoryInputs: Collection<FactoryInputModel> by lazy(NONE) {
        factoryMethod?.parameters?.map { param ->
            object : FactoryInputModel {
                override val payload: InputPayload by lazy(NONE) {
                    InputPayload(param = param)
                }
                override val name get() = param.name

                override fun validate(validator: Validator) {
                    validator.child(payload, Inline)
                }

                override fun toString() = "[param] ${factoryMethod.name}(.., $name: $payload, ..)"
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

    override fun toString() = "[builder] $factoryDeclaration"

    override fun validate(validator: Validator) {
        validator.child(asNode(), kind = Inline)
        for (input in allInputs) {
            validator.child(input)
        }

        if (!factoryDeclaration.isInterface) {
            validator.reportError(Errors.`component creator must be an interface`())
        }
        val factory = factoryMethod
        if (factory == null) {
            validator.reportError(Errors.`missing component creating method`())
        }

        for (function in factoryDeclaration.allPublicFunctions) {
            if (function == factoryMethod || function.parameters.count() == 1 || !function.isAbstract)
                continue
            validator.reportError(Errors.`invalid method in component creator`(method = function))
        }

        // TODO: check for duplicates in modules, dependencies.

        // Check for missing component dependencies
        val providedDependencies = allInputs
            .map { it.payload }
            .filterIsInstance<InputPayload.Dependency>()
            .map { it.dependency }
            .toSet()
        for (missingDependency in createdComponent.dependencies - providedDependencies) {
            validator.reportError(Errors.`missing component dependency`(missing = missingDependency))
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
            validator.reportError(Errors.`missing module`(missing = missingModule))
        }
    }

    private inner class InputPayloadModuleImpl(
        override val module: ModuleModel,
    ) : InputPayload.Module, ClassBackedModel by module {
        override fun validate(validator: Validator) {
            if (!module.requiresInstance ||
                module.isTriviallyConstructable ||
                module !in createdComponent.modules) {
                validator.reportError(Errors.`unneeded module`())
            }
        }

        override fun toString() = module.toString()
    }

    private class InputPayloadInstanceImpl(
        override val node: NodeModel,
    ) : InputPayload.Instance, ClassBackedModel by node {
        override fun validate(validator: Validator) {
            validator.child(node, kind = Inline)
        }

        override fun toString() = node.toString()
    }

    private inner class InputPayloadDependencyImpl(
        override val dependency: ComponentDependencyModel,
    ) : InputPayload.Dependency, ClassBackedModel by dependency {
        override fun validate(validator: Validator) {
            if (dependency !in createdComponent.dependencies) {
                validator.reportError(Errors.`unneeded component dependency`())
            }
        }

        override fun toString() = dependency.toString()
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
