package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.TypeLangModel
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