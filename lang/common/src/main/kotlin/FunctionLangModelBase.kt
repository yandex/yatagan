package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.base.zipOrNull
import com.yandex.daggerlite.lang.CallableLangModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.MemberLangModel

abstract class FunctionLangModelBase : FunctionLangModel {
    final override fun <T> accept(visitor: CallableLangModel.Visitor<T>): T {
        return visitor.visitFunction(this)
    }

    final override fun <R> accept(visitor: MemberLangModel.Visitor<R>): R {
        return visitor.visitFunction(this)
    }

    final override fun compareTo(other: FunctionLangModel): Int {
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