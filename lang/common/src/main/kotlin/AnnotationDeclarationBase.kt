package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.AnnotationDeclaration

abstract class AnnotationDeclarationBase : AnnotationDeclaration {
    final override fun toString(): String {
        return qualifiedName
    }
}