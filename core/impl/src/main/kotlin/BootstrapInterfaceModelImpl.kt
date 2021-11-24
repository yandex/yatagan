package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BootstrapInterface
import com.yandex.daggerlite.BootstrapList
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.BootstrapInterfaceModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.getAnnotation
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class BootstrapInterfaceModelImpl private constructor(
    private val impl: TypeLangModel,
) : BootstrapInterfaceModel {
    init {
        require(canRepresent(impl))
    }

    override fun asNode(factory: LangModelFactory): NodeModel {
        return NodeModelImpl(
            type = factory.getListType(impl),
            qualifier = factory.getAnnotation<BootstrapList>(),
        )
    }

    companion object Factory : ObjectCache<TypeLangModel, BootstrapInterfaceModelImpl>() {
        operator fun invoke(impl: TypeLangModel) = createCached(impl, ::BootstrapInterfaceModelImpl)

        fun canRepresent(type: TypeLangModel): Boolean {
            return type.declaration.isAnnotatedWith<BootstrapInterface>()
        }
    }
}