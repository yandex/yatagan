package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.core.lang.TypeLangModel

abstract class TypeLangModelBase : TypeLangModel {
    final override fun compareTo(other: TypeLangModel): Int {
        return toString().compareTo(other.toString())
    }
}