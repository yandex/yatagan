package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel

fun TypeDeclarationLangModel(declaration: Class<*>): TypeDeclarationLangModel {
    return RtTypeDeclarationImpl(RtTypeImpl(declaration))
}
