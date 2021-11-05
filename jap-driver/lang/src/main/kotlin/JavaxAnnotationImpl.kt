package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import javax.inject.Qualifier
import javax.inject.Scope
import javax.lang.model.element.AnnotationMirror
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxAnnotationImpl private constructor(
    private val impl: AnnotationMirror,
) : CtAnnotationLangModel {
    private val descriptor by lazy(NONE) { describe(impl) }

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
        return impl.typesValue(param = attribute).map(::CtTypeLangModel).memoize()
    }

    override fun getType(attribute: String): TypeLangModel {
        return CtTypeLangModel(impl.typeValue(param = attribute))
    }

    override fun getString(attribute: String): String {
        return impl.stringValue(param = attribute)
    }

    override fun getAnnotations(attribute: String): Sequence<CtAnnotationLangModel> {
        return impl.annotationsValue(param = attribute).map { Factory(it) }.memoize()
    }

    override fun getAnnotation(attribute: String): CtAnnotationLangModel {
        return Factory(impl.annotationValue(param = attribute))
    }

    override fun toString() = descriptor

    companion object Factory : ObjectCache<String, JavaxAnnotationImpl>() {
        private fun describe(impl: AnnotationMirror) = impl.toString()
        operator fun invoke(impl: AnnotationMirror) = createCached(describe(impl)) { JavaxAnnotationImpl(impl) }
    }
}
