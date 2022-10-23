package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.lang.Annotation.Value
import com.yandex.daggerlite.lang.AnnotationValueVisitorAdapter
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.common.AnnotationBase

/**
 * An [Annotation] which supports getting typed attribute values by name efficiently.
 */
abstract class CtAnnotation : AnnotationBase() {
    fun getBoolean(attribute: String): Boolean {
        return attributeValue(attribute).accept(AsBoolean)
    }

    fun getTypes(attribute: String): Sequence<Type> {
        return attributeValue(attribute).accept(AsTypes).asSequence()
    }

    fun getType(attribute: String): Type {
        return attributeValue(attribute).accept(AsType)
    }

    fun getString(attribute: String): String {
        return attributeValue(attribute).accept(AsString)
    }

    fun getAnnotations(attribute: String): Sequence<CtAnnotation> {
        return attributeValue(attribute).accept(AsAnnotations).asSequence()
    }

    private fun attributeValue(attribute: String): Value {
        return getValue(annotationClass.attributes.first { it.name == attribute })
    }

    companion object {
        private object AsBoolean : AnnotationValueVisitorAdapter<Boolean>() {
            override fun visitDefault() = throw IllegalStateException("Expected boolean value")
            override fun visitBoolean(value: Boolean) = value
        }

        private object AsType : AnnotationValueVisitorAdapter<Type>() {
            override fun visitDefault() = throw IllegalStateException("Expected class value")
            override fun visitType(value: Type) = value
        }

        private object AsTypes : AnnotationValueVisitorAdapter<List<Type>>() {
            override fun visitDefault() = throw IllegalStateException("Expected class array value")
            override fun visitArray(value: List<Value>) = value.map { it.accept(AsType) }
            override fun visitType(value: Type) = listOf(value)
        }

        private object AsString : AnnotationValueVisitorAdapter<String>() {
            override fun visitDefault() = throw IllegalStateException("Expected string value")
            override fun visitString(value: String) = value
        }

        private object AsAnnotation : AnnotationValueVisitorAdapter<CtAnnotation>() {
            override fun visitDefault() = throw IllegalStateException("Expected annotation value")
            override fun visitAnnotation(value: Annotation) = value as CtAnnotation
        }

        private object AsAnnotations : AnnotationValueVisitorAdapter<List<CtAnnotation>>() {
            override fun visitDefault() = throw IllegalStateException("Expected annotation array value")
            override fun visitArray(value: List<Value>) = value.map { it.accept(AsAnnotation) }
            override fun visitAnnotation(value: Annotation) = listOf(value as CtAnnotation)
        }
    }
}