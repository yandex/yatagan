package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class ComponentFactoryModelImpl private constructor(
    private val factoryDeclaration: TypeDeclarationLangModel,
) : ComponentFactoryModel {
    private val factoryMethod = checkNotNull(factoryDeclaration.allPublicFunctions.find { it.name == "create" }) {
        "No 'create' method in component factory"
    }

    override val type: TypeLangModel
        get() = factoryDeclaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = factoryDeclaration.asType(),
        forQualifier = null,
    )

    override val createdComponent: ComponentModel
        get() = ComponentModelImpl(factoryMethod.returnType.declaration)

    override val inputs = factoryMethod.parameters.map { param ->
        val declaration = param.type.declaration
        when {
            ComponentModelImpl.canRepresent(declaration) ->
                ComponentDependencyInputImpl(
                    component = ComponentModelImpl(declaration),
                    paramName = param.name,
                )
            ModuleModelImpl.canRepresent(declaration) ->
                ModuleInstanceInputImpl(
                    module = ModuleModelImpl(declaration),
                    paramName = param.name,
                )
            param.isAnnotatedWith<BindsInstance>() ->
                InstanceInputImpl(
                    node = NodeModelImpl(param.type, forQualifier = param),
                    paramName = param.name,
                )
            else -> throw AssertionError("invalid factory method parameter")
        }
    }.toList()

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ComponentFactoryModelImpl>() {
        operator fun invoke(factoryDeclaration: TypeDeclarationLangModel) =
            createCached(factoryDeclaration, ::ComponentFactoryModelImpl)
    }
}
