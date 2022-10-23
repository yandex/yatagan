package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.AnnotationLangModel
import com.yandex.daggerlite.lang.AnnotationLangModel.Value
import com.yandex.daggerlite.lang.AnnotationValueVisitorAdapter
import com.yandex.daggerlite.lang.TypeLangModel
import com.yandex.daggerlite.lang.common.AnnotationLangModelBase

/**
 * An [AnnotationLangModel] which supports getting typed attribute values by name efficiently.
 */
abstract class CtAnnotationLangModel : AnnotationLangModelBase() {
    fun getBoolean(attribute: String): Boolean {
        return attributeValue(attribute).accept(AsBoolean)
    }

    fun getTypes(attribute: String): Sequence<TypeLangModel> {
        return attributeValue(attribute).accept(AsTypes).asSequence()
    }

    fun getType(attribute: String): TypeLangModel {
        return attributeValue(attribute).accept(AsType)
    }

    fun getString(attribute: String): String {
        return attributeValue(attribute).accept(AsString)
    }

    fun getAnnotations(attribute: String): Sequence<CtAnnotationLangModel> {
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

        private object AsType : AnnotationValueVisitorAdapter<TypeLangModel>() {
            override fun visitDefault() = throw IllegalStateException("Expected class value")
            override fun visitType(value: TypeLangModel) = value
        }

        private object AsTypes : AnnotationValueVisitorAdapter<List<TypeLangModel>>() {
            override fun visitDefault() = throw IllegalStateException("Expected class array value")
            override fun visitArray(value: List<Value>) = value.map { it.accept(AsType) }
            override fun visitType(value: TypeLangModel) = listOf(value)
        }

        private object AsString : AnnotationValueVisitorAdapter<String>() {
            override fun visitDefault() = throw IllegalStateException("Expected string value")
            override fun visitString(value: String) = value
        }

        private object AsAnnotation : AnnotationValueVisitorAdapter<CtAnnotationLangModel>() {
            override fun visitDefault() = throw IllegalStateException("Expected annotation value")
            override fun visitAnnotation(value: AnnotationLangModel) = value as CtAnnotationLangModel
        }

        private object AsAnnotations : AnnotationValueVisitorAdapter<List<CtAnnotationLangModel>>() {
            override fun visitDefault() = throw IllegalStateException("Expected annotation array value")
            override fun visitArray(value: List<Value>) = value.map { it.accept(AsAnnotation) }
            override fun visitAnnotation(value: AnnotationLangModel) = listOf(value as CtAnnotationLangModel)
        }
    }
}