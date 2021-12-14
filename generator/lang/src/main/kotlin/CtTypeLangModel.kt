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
}