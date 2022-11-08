package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.TypeDeclaration

abstract class TypeDeclarationBase : TypeDeclaration {
    final override fun toString() = asType().toString()

    final override fun compareTo(other: TypeDeclaration): Int {
        if (this == other) return 0

        // Compare by qualified name - cheap
        qualifiedName.compareTo(other.qualifiedName).let { if (it != 0) return it }
        // Compare by implicit type-arguments - possibly more expensive
        return asType().compareTo(other.asType())
    }
}