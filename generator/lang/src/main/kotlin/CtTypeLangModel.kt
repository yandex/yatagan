package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.TypeLangModel

/**
 * [TypeLangModel] base class, that can be named by [CtTypeNameModel].
 */
interface CtTypeLangModel : TypeLangModel {
    /**
     * Class name.
     * @see CtTypeNameModel
     */
    val nameModel: CtTypeNameModel

    override fun compareTo(other: TypeLangModel): Int {
        if (other !is CtTypeLangModel) return -1
        return nameModel.toString().compareTo(other.nameModel.toString())
    }
}