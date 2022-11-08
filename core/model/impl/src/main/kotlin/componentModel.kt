package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.lang.TypeDeclaration

fun ComponentModel(declaration: TypeDeclaration): ComponentModel {
    return ComponentModelImpl(declaration)
}

fun ComponentFactoryModel(declaration: TypeDeclaration): ComponentFactoryModel {
    return ComponentFactoryModelImpl(declaration)
}
