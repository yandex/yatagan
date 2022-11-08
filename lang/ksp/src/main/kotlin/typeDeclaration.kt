package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.yatagan.lang.TypeDeclaration

fun TypeDeclaration(declaration: KSClassDeclaration): TypeDeclaration {
    return KspTypeDeclarationImpl(KspTypeImpl(declaration.asType(emptyList())))
}