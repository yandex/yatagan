package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.Callable
import com.yandex.yatagan.lang.Constructor

abstract class ConstructorBase : Constructor {
    final override fun <T> accept(visitor: Callable.Visitor<T>): T {
        return visitor.visitConstructor(this)
    }

    final override fun toString() = buildString {
        append(constructee)
        append('(')
        parameters.joinTo(this)
        append(')')
    }
}