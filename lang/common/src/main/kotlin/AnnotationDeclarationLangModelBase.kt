package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel

abstract class AnnotationDeclarationLangModelBase : AnnotationDeclarationLangModel {
    final override fun toString(): String {
        return qualifiedName
    }
}