package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.lang.TypeDeclaration

fun TypeDeclaration(declaration: Class<*>): TypeDeclaration {
    return RtTypeDeclarationImpl(RtTypeImpl(declaration))
}
