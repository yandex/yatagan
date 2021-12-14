package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.ListDeclarationModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.DeclareListAnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel

internal class ListDeclarationImpl(
    private val impl: FunctionLangModel,
    private val declareList: DeclareListAnnotationLangModel,
) : ListDeclarationModel {
    override val listType: NodeModel
        get() = NodeModelImpl(impl.returnType)
    override val orderByDependency: Boolean
        get() = declareList.orderByDependency
}