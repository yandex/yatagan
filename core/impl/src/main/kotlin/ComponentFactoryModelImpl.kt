package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.core.ComponentDependencyFactoryInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.InstanceBinding
import com.yandex.daggerlite.core.ModuleInstanceFactoryInput
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class ComponentFactoryModelImpl(
    override val createdComponent: ComponentModel,
    private val factoryDeclaration: TypeDeclarationLangModel,
    factoryMethod: FunctionLangModel,
) : ComponentFactoryModel() {
    override val type: TypeLangModel
        get() = factoryDeclaration.asType()

    override val inputs = factoryMethod.parameters.map { param ->
        val declaration = param.type.declaration
        when {
            ComponentModelImpl.canRepresent(declaration) ->
                ComponentDependencyFactoryInput(ComponentModelImpl(declaration), param.name)
            ModuleModelImpl.canRepresent(declaration) ->
                ModuleInstanceFactoryInput(ModuleModelImpl(declaration), param.name)
            param.isAnnotatedWith<BindsInstance>() ->
                InstanceBinding(NodeModelImpl(param.type, forQualifier = param), param.name)
            else -> throw IllegalStateException("invalid factory method parameter")
        }
    }.toList()
}
