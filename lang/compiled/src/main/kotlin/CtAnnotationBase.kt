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

package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Annotation.Value
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.common.AnnotationBase

/**
 * An [Annotation] which supports getting typed attribute values by name efficiently.
 */
abstract class CtAnnotationBase : AnnotationBase() {
    fun getBoolean(attribute: String): Boolean {
        return attributeValue(attribute).accept(AsBoolean)
    }

    fun getTypes(attribute: String): List<Type> {
        return attributeValue(attribute).accept(AsTypes)
    }

    fun getType(attribute: String): Type {
        return attributeValue(attribute).accept(AsType)
    }

    fun getString(attribute: String): String {
        return attributeValue(attribute).accept(AsString)
    }

    fun getAnnotations(attribute: String): List<CtAnnotationBase> {
        return attributeValue(attribute).accept(AsAnnotations)
    }

    private fun attributeValue(attribute: String): Value {
        return getValue(annotationClass.attributes.first { it.name == attribute })
    }

    companion object {
        private object AsBoolean : Value.Visitor<Boolean> {
            override fun visitDefault(value: Any?) = throw IllegalStateException("Expected boolean value, got $value")
            override fun visitBoolean(value: Boolean) = value
        }

        private object AsType : Value.Visitor<Type> {
            override fun visitDefault(value: Any?) = throw IllegalStateException("Expected class value, got $value")
            override fun visitType(value: Type) = value
        }

        private object AsTypes : Value.Visitor<List<Type>> {
            override fun visitDefault(value: Any?) = throw IllegalStateException("Expected class array value, got $value")
            override fun visitArray(value: List<Value>) = value.map { it.accept(AsType) }
            override fun visitType(value: Type) = listOf(value)
        }

        private object AsString : Value.Visitor<String> {
            override fun visitDefault(value: Any?) = throw IllegalStateException("Expected string value, got $value")
            override fun visitString(value: String) = value
        }

        private object AsAnnotation : Value.Visitor<CtAnnotationBase> {
            override fun visitDefault(value: Any?) = throw IllegalStateException("Expected annotation value, got $value")
            override fun visitAnnotation(value: Annotation) = value as CtAnnotationBase
        }

        private object AsAnnotations : Value.Visitor<List<CtAnnotationBase>> {
            override fun visitDefault(value: Any?) = throw IllegalStateException("Expected annotation array value, got $value")
            override fun visitArray(value: List<Value>) = value.map { it.accept(AsAnnotation) }
            override fun visitAnnotation(value: Annotation) = listOf(value as CtAnnotationBase)
        }
    }
}