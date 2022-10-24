package com.yandex.daggerlite.lang.jap

import com.yandex.daggerlite.lang.TypeDeclaration
import javax.lang.model.element.TypeElement

fun TypeDeclaration(typeElement: TypeElement): TypeDeclaration {
    return JavaxTypeDeclarationImpl(typeElement.asType().asDeclaredType())
}