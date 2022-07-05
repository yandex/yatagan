package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.DeclareList
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ListDeclarationModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class ListDeclarationImpl private constructor(
    override val listType: NodeModel,
) : ListDeclarationModel {

    companion object Factory : ObjectCache<NodeModel, ListDeclarationImpl>() {
        operator fun invoke(function: FunctionLangModel): ListDeclarationImpl {
            assert(function.isAnnotatedWith<DeclareList>()) { "Not reached" }
            return Factory(NodeModelImpl(
                type = function.returnType,
                forQualifier = function,
            ))
        }

        operator fun invoke(listType: NodeModel) = createCached(listType, ::ListDeclarationImpl)
    }
}