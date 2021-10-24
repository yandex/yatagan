package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.TypeLangModel

abstract class ClassBackedModel {
    abstract val type: TypeLangModel

    override fun equals(other: Any?): Boolean {
        return this === other || other is ClassBackedModel && type == other.type
    }

    override fun hashCode() = type.hashCode()
}