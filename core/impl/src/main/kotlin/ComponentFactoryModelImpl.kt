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
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.reportError
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
                if (!method.returnType.isVoid && !method.returnType.isAssignableFrom(factoryDeclaration.asType())) {
                    validator.reportError(Errors.`invalid builder setter return type`(
                        creatorType = factoryDeclaration.asType(),
                    ))
                }
            }
        }
    }.toList()

    override val factoryInputs: Collection<FactoryInputModel> by lazy(NONE) {
        factoryMethod?.parameters?.map { param ->
            object : FactoryInputModel {
                override val payload: InputPayload by lazy(NONE) {
                    InputPayload(param = param)
                }
                override val name get() = param.name
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

    override fun toString() = "ComponentFactory[$factoryDeclaration]"

    override fun validate(validator: Validator) {
        validator.child(asNode(), kind = Inline)
        for (builderInput in builderInputs) {
            validator.child(builderInput)
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

        val providedComponents = allInputs
            .map { it.payload }
            .filterIsInstance<InputPayload.Dependency>()
            .map { it.dependency }
            .toSet()
        if (createdComponent.dependencies != providedComponents) {
            val missing = createdComponent.dependencies - providedComponents
            for (missingDependency in missing) {
                validator.reportError(Errors.`missing component dependency`(missing = missingDependency))
            }
            val unneeded = providedComponents - createdComponent.dependencies
            for (extraDependency in unneeded) {
                validator.reportError(Errors.`unneeded component dependency`(extra = extraDependency))
            }
        }

        val providedModules = allInputs
            .map { it.payload }
            .filterIsInstance<InputPayload.Module>()
            .map { it.module }
            .toSet()
        val allModulesRequiresInstance = createdComponent.modules.asSequence()
            .filter(ModuleModel::requiresInstance).toMutableSet()

        val missing = (allModulesRequiresInstance - providedModules).filter { !it.isTriviallyConstructable }
        for (missingModule in missing) {
            validator.reportError(Errors.`missing module`(missing = missingModule))
        }
        val unneeded = providedModules - allModulesRequiresInstance
        if (unneeded.isNotEmpty()) {
            for (extraModule in unneeded) {
                validator.reportError(Errors.`unneeded module`(extra = extraModule))
            }
        }
    }

    private class InputPayloadModuleImpl(
        override val module: ModuleModel,
    ) : InputPayload.Module, ClassBackedModel by module {
        override fun validate(validator: Validator) {
            validator.child(module, kind = Inline)
        }
    }

    private class InputPayloadInstanceImpl(
        override val node: NodeModel,
    ) : InputPayload.Instance, ClassBackedModel by node {
        override fun validate(validator: Validator) {
            validator.child(node, kind = Inline)
        }
    }

    private class InputPayloadDependencyImpl(
        override val dependency: ComponentDependencyModel,
    ) : InputPayload.Dependency, ClassBackedModel by dependency {
        override fun validate(validator: Validator) {
            validator.child(dependency, kind = Inline)
        }
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
