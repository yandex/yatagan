package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.lang.Parameter

abstract class ParameterBase : Parameter {
    final override fun toString(): String {
        return "$name: $type"
    }
}