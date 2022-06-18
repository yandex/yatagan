package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter

internal class JavaxAnnotationClassImpl private constructor(
    impl: TypeElement,
) : AnnotationDeclarationLangModel, JavaxAnnotatedImpl<TypeElement>(impl) {

    override val attributes: Sequence<AnnotationDeclarationLangModel.Attribute> by lazy {
        ElementFilter.methodsIn(impl.enclosedElements)
            .asSequence()
            .filter { it.isAbstract }
            .map {
                AttributeImpl(
                    name = it.simpleName.toString(),
                    type = JavaxTypeImpl(it.returnType),
                )
            }
            .memoize()
    }

    override fun isClass(clazz: Class<out Annotation>): Boolean {
        return impl.qualifiedName.contentEquals(clazz.canonicalName)
    }

    private class AttributeImpl(
        override val name: String,
        override val type: TypeLangModel,
    ) : AnnotationDeclarationLangModel.Attribute

    companion object Factory : ObjectCache<TypeElement, JavaxAnnotationClassImpl>() {
        operator fun invoke(type: TypeElement) = createCached(type) { JavaxAnnotationClassImpl(type) }
    }
}