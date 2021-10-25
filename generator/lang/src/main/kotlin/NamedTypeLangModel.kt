package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.TypeLangModel

/**
 * [TypeLangModel] base class, that can be named by [ClassNameModel] and implements [equals]/[hashCode] by the name.
 */
abstract class NamedTypeLangModel : TypeLangModel {
    /**
     * Class name.
     * @see ClassNameModel
     */
    abstract val name: ClassNameModel

    final override fun equals(other: Any?): Boolean {
        return this === other || (other is NamedTypeLangModel && name == other.name)
    }

    final override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString() = name.toString()
}