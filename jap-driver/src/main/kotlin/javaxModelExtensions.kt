package com.yandex.daggerlite.jap

import com.yandex.daggerlite.core.ClassNameModel
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror

fun classNameModel(type: TypeMirror): ClassNameModel {
    val typeArgs = (type as? DeclaredType)?.typeArguments?.map { classNameModel(it) } ?: emptyList()
    return classNameModel(type.asTypeElement()).copy(typeArguments = typeArgs)
}

fun classNameModel(type: TypeElement): ClassNameModel {
    val packageName = type.getPackageElement().qualifiedName.toString()
    val simpleNames = type.qualifiedName.substring(packageName.length + 1).split('.')

    return ClassNameModel(packageName, simpleNames, emptyList())
}