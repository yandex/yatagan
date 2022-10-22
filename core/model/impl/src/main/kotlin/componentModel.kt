package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.core.model.ComponentFactoryModel
import com.yandex.daggerlite.core.model.ComponentModel
import com.yandex.daggerlite.lang.TypeDeclarationLangModel

fun ComponentModel(declaration: TypeDeclarationLangModel): ComponentModel {
    return ComponentModelImpl(declaration)
}

fun ComponentFactoryModel(declaration: TypeDeclarationLangModel): ComponentFactoryModel {
    return ComponentFactoryModelImpl(declaration)
}
