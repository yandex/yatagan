package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.TypeLangModel
import com.yandex.daggerlite.lang.common.TypeLangModelBase

/**
 * [TypeLangModel] base class, that can be named by [CtTypeNameModel].
 */
abstract class CtTypeLangModel : TypeLangModelBase() {
    /**
     * Class name.
     * @see CtTypeNameModel
     */
    abstract val nameModel: CtTypeNameModel

    final override fun toString() = nameModel.toString()
}