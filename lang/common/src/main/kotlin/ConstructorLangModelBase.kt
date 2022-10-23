package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.lang.CallableLangModel
import com.yandex.daggerlite.lang.ConstructorLangModel

abstract class ConstructorLangModelBase : ConstructorLangModel {
    final override fun <T> accept(visitor: CallableLangModel.Visitor<T>): T {
        return visitor.visitConstructor(this)
    }

    final override fun toString() = buildString {
        append(constructee)
        append('(')
        parameters.joinTo(this)
        append(')')
    }
}