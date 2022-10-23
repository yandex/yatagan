package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.lang.AnnotationDeclaration

abstract class AnnotationDeclarationBase : AnnotationDeclaration {
    final override fun toString(): String {
        return qualifiedName
    }
}