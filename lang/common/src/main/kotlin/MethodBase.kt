package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.base.zipOrNull
import com.yandex.daggerlite.lang.Callable
import com.yandex.daggerlite.lang.Member
import com.yandex.daggerlite.lang.Method

abstract class MethodBase : Method {
    final override fun <T> accept(visitor: Callable.Visitor<T>): T {
        return visitor.visitMethod(this)
    }

    final override fun <R> accept(visitor: Member.Visitor<R>): R {
        return visitor.visitMethod(this)
    }

    final override fun compareTo(other: Method): Int {
        if (this == other) return 0

        name.compareTo(other.name).let { if (it != 0) return it }
        owner.compareTo(other.owner).let { if (it != 0) return it }
        for ((p1, p2) in parameters.zipOrNull(other.parameters)) {
            if (p1 == null) return -1
            if (p2 == null) return +1
            p1.type.compareTo(p2.type).let { if (it != 0) return it }
        }
        returnType.compareTo(other.returnType).let { if (it != 0) return it }
        return 0
    }

    final override fun toString() = buildString {
        append(owner)
        append("::")
        append(name).append('(')
        parameters.joinTo(this)
        append("): ")
        append(returnType)
    }
}