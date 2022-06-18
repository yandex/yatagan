package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

internal class RtAnnotationClassImpl private constructor(
    clazz: Class<*>,
) : AnnotationDeclarationLangModel, RtAnnotatedImpl<Class<*>>(clazz) {

    override val attributes: Sequence<AnnotationDeclarationLangModel.Attribute> by lazy {
        clazz.declaredMethods.asSequence()
            .filter { it.isAbstract }
            .map {
                AttributeImpl(
                    name = it.name,
                    type = RtTypeImpl(it.returnType),
                )
            }
    }

    override fun isClass(clazz: Class<out Annotation>): Boolean {
        return impl == clazz
    }

    private class AttributeImpl(
        override val name: String,
        override val type: TypeLangModel,
    ) : AnnotationDeclarationLangModel.Attribute

    companion object Factory : ObjectCache<Class<*>, RtAnnotationClassImpl>() {
        operator fun invoke(clazz: Class<*>) = createCached(clazz) { RtAnnotationClassImpl(clazz) }
    }
}