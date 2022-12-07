/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Type

abstract class AnnotationBase : Annotation {
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

    abstract class ValueBase : Annotation.Value {
        final override fun toString(): String = this.accept(ToString)
    }
}

private object ToString : Annotation.Value.Visitor<String> {
    override fun visitBoolean(value: Boolean) = value.toString()
    override fun visitByte(value: Byte) = value.toString()
    override fun visitShort(value: Short) = value.toString()
    override fun visitInt(value: Int) = value.toString()
    override fun visitLong(value: Long) = value.toString()
    override fun visitChar(value: Char) = "'$value'"
    override fun visitFloat(value: Float) = value.toString()
    override fun visitDouble(value: Double) = value.toString()
    override fun visitString(value: String) = "\"$value\""
    override fun visitType(value: Type) = "$value.class"
    override fun visitAnnotation(value: Annotation) = value.toString()
    override fun visitEnumConstant(enum: Type, constant: String) = "$enum.$constant"
    override fun visitUnresolved(): String = "<unresolved>"
    override fun visitArray(value: List<Annotation.Value>): String {
        return value.joinToString(prefix = "{", postfix = "}", separator = ", ") { it.accept(ToString) }
    }
}