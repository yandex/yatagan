package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

abstract class AnnotationLangModelBase : AnnotationLangModel {
    final override fun toString() = buildString {
        append('@')
        append(annotationClass)
        val attributes = annotationClass.attributes
        if (attributes.any()) {
            attributes
                .sortedBy { it.name }
                .joinTo(this, prefix = "(", postfix = ")", separator = ", ") {
                    "${it.name}=${getValue(it).accept(ToString)}"
                }
        }
    }

    abstract class ValueBase : AnnotationLangModel.Value {
        final override fun toString(): String = this.accept(ToString)
    }
}

private object ToString : AnnotationLangModel.Value.Visitor<String> {
    override fun visitBoolean(value: Boolean) = value.toString()
    override fun visitByte(value: Byte) = value.toString()
    override fun visitShort(value: Short) = value.toString()
    override fun visitInt(value: Int) = value.toString()
    override fun visitLong(value: Long) = value.toString()
    override fun visitChar(value: Char) = "'$value'"
    override fun visitFloat(value: Float) = value.toString()
    override fun visitDouble(value: Double) = value.toString()
    override fun visitString(value: String) = "\"$value\""
    override fun visitType(value: TypeLangModel) = "$value.class"
    override fun visitAnnotation(value: AnnotationLangModel) = value.toString()
    override fun visitEnumConstant(enum: TypeLangModel, constant: String) = "$enum.$constant"
    override fun visitUnresolved(): String = "<unresolved>"
    override fun visitArray(value: List<AnnotationLangModel.Value>): String {
        return value.joinToString(prefix = "{", postfix = "}", separator = ", ") { it.accept(ToString) }
    }
}