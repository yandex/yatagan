package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel

internal class RtAnnotationImpl(
    private val impl: Annotation,
) : AnnotationLangModel {

    override val annotationClass: AnnotationDeclarationLangModel
        get() = RtAnnotationClassImpl(impl.javaAnnotationClass)

    override fun toString() = formatString(impl)

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RtAnnotationImpl && impl == other.impl)
    }

    override fun hashCode(): Int = impl.hashCode()

    companion object  {
        private fun formatString(value: Any?): String = when (value) {
            is String -> "\"$value\""
            is ByteArray -> value.joinToString(prefix = "{", postfix = "}")
            is CharArray -> value.joinToString(prefix = "{", postfix = "}")
            is DoubleArray -> value.joinToString(prefix = "{", postfix = "}")
            is FloatArray -> value.joinToString(prefix = "{", postfix = "}")
            is IntArray -> value.joinToString(prefix = "{", postfix = "}")
            is LongArray -> value.joinToString(prefix = "{", postfix = "}")
            is ShortArray -> value.joinToString(prefix = "{", postfix = "}")
            is BooleanArray -> value.joinToString(prefix = "{", postfix = "}")
            is Array<*> -> value.joinToString(prefix = "{", postfix = "}", transform = ::formatString)
            is Class<*> -> value.canonicalName
            is Enum<*> -> "${value.declaringClass.canonicalName}.${value.name}"
            is Annotation -> buildString {
                append('@')
                append(value.javaAnnotationClass.canonicalName)
                val attributes = value.javaAnnotationClass.methods
                    .asSequence()
                    .filter { it.declaringClass.isAnnotation }
                if (attributes.any()) {
                    attributes
                        .sortedBy { it.name }
                        .joinTo(this, prefix = "(", postfix = ")") {
                            val attributeValue = formatString(it.invoke(value))
                            "${it.name}=$attributeValue"
                        }
                }
            }

            else -> value.toString()
        }
    }
}
