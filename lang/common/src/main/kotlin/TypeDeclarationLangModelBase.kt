package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel

abstract class TypeDeclarationLangModelBase : TypeDeclarationLangModel {
    final override fun toString() = asType().toString()

    final override fun compareTo(other: TypeDeclarationLangModel): Int {
        if (this == other) return 0

        // Compare by qualified name - cheap
        qualifiedName.compareTo(other.qualifiedName).let { if (it != 0) return it }
        // Compare by implicit type-arguments - possibly more expensive
        return asType().compareTo(other.asType())
    }
}