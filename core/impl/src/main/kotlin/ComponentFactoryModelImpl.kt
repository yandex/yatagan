package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
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
}
