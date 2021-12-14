package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentFactoryModel.BuilderInputModel
import com.yandex.daggerlite.core.ComponentFactoryModel.FactoryInputModel
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import kotlin.LazyThreadSafetyMode.NONE

internal class ComponentFactoryModelImpl private constructor(
    private val factoryDeclaration: TypeDeclarationLangModel,
    override val createdComponent: ComponentModel,
) : ComponentFactoryModel {

    override val factoryMethod = checkNotNull(factoryDeclaration.allPublicFunctions.find {
        it.isAbstract && it.returnType == createdComponent.type
    }) {
        "No creating method in $factoryDeclaration is detected"
    }

    override val type: TypeLangModel
        get() = factoryDeclaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = factoryDeclaration.asType(),
        forQualifier = null,
    )

    override val builderInputs: Collection<BuilderInputModel> = factoryDeclaration.allPublicFunctions.filter {
        it.isAbstract && it != factoryMethod
    }.map { method ->
        object : BuilderInputModel {
            override val payload: InputPayload by lazy(NONE) {
                InputPayload(
                    param = method.parameters.single(),
                    forBindsInstance = method,
                )
            }
            override val name get() = method.name
            override val builderSetter get() = method
        }
    }.toList()

    override val factoryInputs: Collection<FactoryInputModel> = factoryMethod.parameters.map { param ->
        object : FactoryInputModel {
            override val payload: InputPayload by lazy(NONE) {
                InputPayload(param = param)
            }
            override val name get() = param.name
        }
    }.toList()

    private fun InputPayload(
        param: ParameterLangModel,
        forBindsInstance: AnnotatedLangModel = param,
    ): InputPayload {
        val declaration = param.type.declaration
        return when {
            ModuleModelImpl.canRepresent(declaration) ->
                InputPayload.Module(module = ModuleModelImpl(declaration))
            forBindsInstance.isAnnotatedWith<BindsInstance>() ->
                InputPayload.Instance(node = NodeModelImpl(param.type, forQualifier = param))
            ComponentDependencyModelImpl.canRepresent(declaration) ->
                InputPayload.Dependency(dependency = ComponentDependencyModelImpl(declaration.asType()))
            else -> throw IllegalStateException(
                "Invalid creator input $declaration in $forBindsInstance in $factoryDeclaration")
        }
    }

    override fun toString() = "ComponentFactory[$factoryDeclaration]"

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
