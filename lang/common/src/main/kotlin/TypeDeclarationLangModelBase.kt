package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel

abstract class TypeDeclarationLangModelBase : TypeDeclarationLangModel {
    final override fun toString() = asType().toString()
}