package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.TypeDeclarationLangModel

fun TypeDeclarationLangModel(declaration: Class<*>): TypeDeclarationLangModel {
    return RtTypeDeclarationImpl(RtTypeImpl(declaration))
}
