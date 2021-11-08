package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class ComponentFactoryModelImpl private constructor(
    private val factoryDeclaration: TypeDeclarationLangModel,
    override val createdComponent: ComponentModel,
) : ComponentFactoryModel {

    private val factoryMethod = checkNotNull(factoryDeclaration.allPublicFunctions.find {
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

    override val factoryFunctionName: String
        get() = factoryMethod.name

    override val builderInputs: Collection<ComponentFactoryModel.Input> = factoryDeclaration.allPublicFunctions.filter {
        it.isAbstract && it != factoryMethod
    }.map {
        toInput(
            param = it.parameters.single(),
            forBindsInstance = it,
            name = it.name,
        )
    }.toList()

    override val factoryInputs = factoryMethod.parameters.map(::toInput).toList()

    private fun toInput(
        param: ParameterLangModel,
        forBindsInstance: AnnotatedLangModel = param,
        name: String = param.name,
    ): ComponentFactoryModel.Input {
        val declaration = param.type.declaration
        return when {
            ComponentModelImpl.canRepresent(declaration) ->
                ComponentDependencyInputImpl(
                    component = ComponentModelImpl(declaration),
                    name = name,
                )
            ModuleModelImpl.canRepresent(declaration) ->
                ModuleInstanceInputImpl(
                    module = ModuleModelImpl(declaration),
                    name = name,
                )
            forBindsInstance.isAnnotatedWith<BindsInstance>() ->
                InstanceInputImpl(
                    node = NodeModelImpl(param.type, forQualifier = param),
                    name = name,
                )
            else -> throw AssertionError("invalid creator input $declaration in $forBindsInstance in $factoryDeclaration")
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
