package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.lang.TypeLangModel

abstract class TypeLangModelBase : TypeLangModel {
    final override fun compareTo(other: TypeLangModel): Int {
        if (this == other) return 0

        // Use string representation for stable ordering across all implementations.
        return toString().compareTo(other.toString())
    }
}