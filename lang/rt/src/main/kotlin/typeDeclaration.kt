package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.TypeDeclaration

fun TypeDeclaration(declaration: Class<*>): TypeDeclaration {
    return RtTypeDeclarationImpl(RtTypeImpl(declaration))
}
