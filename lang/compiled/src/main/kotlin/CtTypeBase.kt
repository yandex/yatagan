package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.common.TypeBase

/**
 * [Type] base class, that can be named by [CtTypeNameModel].
 */
abstract class CtTypeBase : TypeBase() {
    /**
     * Class name.
     * @see CtTypeNameModel
     */
    abstract val nameModel: CtTypeNameModel

    final override fun toString() = nameModel.toString()
}