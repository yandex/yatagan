package com.yandex.daggerlite.lang.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.lang.TypeDeclarationLangModel

fun TypeDeclarationLangModel(declaration: KSClassDeclaration): TypeDeclarationLangModel {
    return KspTypeDeclarationImpl(KspTypeImpl(declaration.asType(emptyList())))
}