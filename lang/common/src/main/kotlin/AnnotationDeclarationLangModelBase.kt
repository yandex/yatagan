package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.lang.AnnotationDeclarationLangModel

abstract class AnnotationDeclarationLangModelBase : AnnotationDeclarationLangModel {
    final override fun toString(): String {
        return qualifiedName
    }
}