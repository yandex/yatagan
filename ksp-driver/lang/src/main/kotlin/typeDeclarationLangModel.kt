package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel

fun TypeDeclarationLangModel(declaration: KSClassDeclaration): TypeDeclarationLangModel {
    return KspTypeDeclarationImpl(declaration.asType(emptyList()))
}