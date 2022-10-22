package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.lang.ParameterLangModel

abstract class ParameterLangModelBase : ParameterLangModel {
    final override fun toString(): String {
        return "$name: $type"
    }
}