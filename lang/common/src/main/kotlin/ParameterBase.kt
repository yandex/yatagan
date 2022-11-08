package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.Parameter

abstract class ParameterBase : Parameter {
    final override fun toString(): String {
        return "$name: $type"
    }
}