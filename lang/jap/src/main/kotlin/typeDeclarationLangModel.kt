package com.yandex.daggerlite.lang.jap

import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import javax.lang.model.element.TypeElement

fun TypeDeclarationLangModel(typeElement: TypeElement): TypeDeclarationLangModel {
    return JavaxTypeDeclarationImpl(typeElement.asType().asDeclaredType())
}