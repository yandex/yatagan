package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.core.model.ComponentFactoryModel
import com.yandex.daggerlite.core.model.ComponentModel
import com.yandex.daggerlite.lang.TypeDeclaration

fun ComponentModel(declaration: TypeDeclaration): ComponentModel {
    return ComponentModelImpl(declaration)
}

fun ComponentFactoryModel(declaration: TypeDeclaration): ComponentFactoryModel {
    return ComponentFactoryModelImpl(declaration)
}
