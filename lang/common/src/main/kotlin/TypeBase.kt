package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.Type

abstract class TypeBase : Type {
    final override fun compareTo(other: Type): Int {
        if (this == other) return 0

        // Use string representation for stable ordering across all implementations.
        return toString().compareTo(other.toString())
    }
}