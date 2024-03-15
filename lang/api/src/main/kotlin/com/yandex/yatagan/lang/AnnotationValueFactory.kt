package com.yandex.yatagan.lang

import com.yandex.yatagan.lang.Annotation.Value
import com.yandex.yatagan.lang.AnnotationDeclaration.Attribute

public interface AnnotationValueFactory {
    public fun valueOf(value: Boolean): Value
    public fun valueOf(value: Byte): Value
    public fun valueOf(value: Short): Value
    public fun valueOf(value: Int): Value
    public fun valueOf(value: Long): Value
    public fun valueOf(value: Char): Value
    public fun valueOf(value: Float): Value
    public fun valueOf(value: Double): Value
    public fun valueOf(value: String): Value
    public fun valueOf(value: Type): Value
    public fun valueOf(value: Annotation): Value
    public fun valueOf(enum: Type, constant: String): Value
    public fun valueOf(value: List<Value>): Value
}
