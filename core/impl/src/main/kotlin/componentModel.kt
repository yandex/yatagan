package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel

fun ComponentModel(declaration: TypeDeclarationLangModel): ComponentModel {
    return ComponentModelImpl(declaration)
}