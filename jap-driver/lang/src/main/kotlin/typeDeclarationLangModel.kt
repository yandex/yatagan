package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import javax.lang.model.element.TypeElement

fun TypeDeclarationLangModel(typeElement: TypeElement): TypeDeclarationLangModel {
    return JavaxTypeDeclarationImpl(typeElement.asType().asDeclaredType())
}