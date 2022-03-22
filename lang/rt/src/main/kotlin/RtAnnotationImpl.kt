package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import javax.inject.Qualifier
import javax.inject.Scope

internal class RtAnnotationImpl private constructor(
    private val impl: Annotation,
) : AnnotationLangModel {

    override val isScope: Boolean by lazy {
        impl.annotationClass.java.isAnnotationPresent(Scope::class.java)
    }

    override val isQualifier: Boolean by lazy {
        impl.annotationClass.java.isAnnotationPresent(Qualifier::class.java)
    }

    override fun <A : Annotation> hasType(type: Class<A>): Boolean {
        return impl.annotationClass.java == type
    }

    override fun toString() = formatString(impl)

    companion object Factory : ObjectCache<Annotation, RtAnnotationImpl>() {
        operator fun invoke(annotation: Annotation) = createCached(annotation, ::RtAnnotationImpl)

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
                append(value.annotationClass.java.canonicalName)
                val attributes = value.annotationClass.java.methods
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
