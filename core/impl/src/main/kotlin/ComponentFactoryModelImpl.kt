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
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Inline
import com.yandex.daggerlite.validation.impl.buildError
import kotlin.LazyThreadSafetyMode.NONE

internal class ComponentFactoryModelImpl private constructor(
    private val factoryDeclaration: TypeDeclarationLangModel,
    override val createdComponent: ComponentModel,
) : ComponentFactoryModel {

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
                    validator.report(buildError {
                        contents = "Setter $method in component creator must return either void or creator type itself"
                    })
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
            ComponentDependencyModelImpl.canRepresent(declaration) ->
                InputPayloadDependencyImpl(dependency = ComponentDependencyModelImpl(declaration.asType()))
            else -> throw IllegalStateException(
                "Invalid creator input $declaration in $forBindsInstance in $factoryDeclaration")
        }
    }

    override fun toString() = "ComponentFactory[$factoryDeclaration]"

    override fun validate(validator: Validator) {
        validator.child(asNode(), kind = Inline)
        for (builderInput in builderInputs) {
            validator.child(builderInput)
        }

        if (!factoryDeclaration.isInterface) {
            validator.report(buildError {
                contents = "Component creator declaration must be an interface"
            })
        }
        val factory = factoryMethod
        if (factory == null) {
            validator.report(buildError {
                contents = "No component creating method is found"
            })
        }

        for (function in factoryDeclaration.allPublicFunctions) {
            if (function == factoryMethod || function.parameters.count() == 1 || !function.isAbstract)
                continue
            validator.report(buildError {
                contents = "Invalid method in component creator: $function"
            })
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
            createdComponent: ComponentModel,
        ) = createCached(factoryDeclaration) {
            ComponentFactoryModelImpl(
                factoryDeclaration = it,
                createdComponent = createdComponent,
            )
        }

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.isAnnotatedWith<Component.Builder>()
        }
    }
}
