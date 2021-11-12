package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.TypeLangModel

/**
 * [TypeLangModel] base class, that can be named by [CtTypeNameModel] and implements [equals]/[hashCode] by the name.
 */
abstract class CtTypeLangModel : TypeLangModel {
    /**
     * Class name.
     * @see CtTypeNameModel
     */
    abstract val name: CtTypeNameModel

    override fun toString() = name.toString()
}