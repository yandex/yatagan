package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member

abstract class FieldBase : Field {
    final override fun <R> accept(visitor: Member.Visitor<R>): R {
        return visitor.visitField(this)
    }

    final override fun toString() = "$owner::$name: $type"
}