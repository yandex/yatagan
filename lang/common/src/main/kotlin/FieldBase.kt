package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.lang.Field
import com.yandex.daggerlite.lang.Member

abstract class FieldBase : Field {
    final override fun <R> accept(visitor: Member.Visitor<R>): R {
        return visitor.visitField(this)
    }

    final override fun toString() = "$owner::$name: $type"
}