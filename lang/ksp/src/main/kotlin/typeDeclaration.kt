package com.yandex.daggerlite.lang.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.lang.TypeDeclaration

fun TypeDeclaration(declaration: KSClassDeclaration): TypeDeclaration {
    return KspTypeDeclarationImpl(KspTypeImpl(declaration.asType(emptyList())))
}