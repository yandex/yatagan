package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.core.lang.CallableLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel

abstract class FunctionLangModelBase : FunctionLangModel {
    final override fun <T> accept(visitor: CallableLangModel.Visitor<T>): T {
        return visitor.visitFunction(this)
    }

    final override fun <R> accept(visitor: MemberLangModel.Visitor<R>): R {
        return visitor.visitFunction(this)
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