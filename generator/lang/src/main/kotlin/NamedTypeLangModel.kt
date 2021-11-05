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

    override fun toString() = name.toString()

    override val isBoolean: Boolean
        get() = with(name) {
            // `Types.unboxedType()` throws, so better use heuristics.
            packageName == "java.lang" && simpleNames.singleOrNull() == "Boolean"
        }
}