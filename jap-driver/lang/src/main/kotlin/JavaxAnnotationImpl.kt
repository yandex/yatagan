package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.CompileTimeAnnotationLangModel
import javax.inject.Qualifier
import javax.inject.Scope
import javax.lang.model.element.AnnotationMirror
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxAnnotationImpl(
    private val impl: AnnotationMirror,
) : CompileTimeAnnotationLangModel {
    private val descriptor by lazy(NONE) { impl.toString() }

    override val isScope: Boolean
        get() = impl.annotationType.asElement().isAnnotatedWith<Scope>()
    override val isQualifier: Boolean
        get() = impl.annotationType.asElement().isAnnotatedWith<Qualifier>()

    override fun <A : Annotation> hasType(type: Class<A>): Boolean {
        return impl.annotationType.asTypeElement().qualifiedName.contentEquals(type.canonicalName)
    }

    override fun getBoolean(attribute: String): Boolean {
        return impl.booleanValue(param = attribute)
    }

    override fun getTypes(attribute: String): Sequence<TypeLangModel> {
        return impl.typesValue(param = attribute).map(::NamedTypeLangModel).memoize()
    }

    override fun getType(attribute: String): TypeLangModel {
        return NamedTypeLangModel(impl.typeValue(param = attribute))
    }

    override fun getString(attribute: String): String {
        return impl.stringValue(param = attribute)
    }

    override fun getAnnotations(attribute: String): Sequence<CompileTimeAnnotationLangModel> {
        return impl.annotationsValue(param = attribute).map(::JavaxAnnotationImpl).memoize()
    }

    override fun getAnnotation(attribute: String): CompileTimeAnnotationLangModel {
        return JavaxAnnotationImpl(impl.annotationValue(param = attribute))
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is JavaxAnnotationImpl && other.descriptor == descriptor)
    }

    override fun hashCode() = descriptor.hashCode()

    override fun toString() = descriptor
}
