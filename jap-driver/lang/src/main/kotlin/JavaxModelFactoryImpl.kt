package com.yandex.daggerlite.jap.lang

import com.google.auto.common.SimpleAnnotationMirror
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.lang.model.element.TypeElement

class JavaxModelFactoryImpl : LangModelFactory {
    private val listElement: TypeElement by lazy {
        Utils.elements.getTypeElement(java.util.List::class.java.canonicalName)
    }
    private val collectionElement: TypeElement by lazy {
        Utils.elements.getTypeElement(java.util.Collection::class.java.canonicalName)
    }

    override fun getAnnotation(clazz: Class<out Annotation>): AnnotationLangModel {
        return JavaxAnnotationImpl(SimpleAnnotationMirror.of(Utils.elements.getTypeElement(clazz.canonicalName)))
    }

    override fun getListType(type: TypeLangModel): TypeLangModel {
        return JavaxTypeImpl(Utils.types.getDeclaredType(listElement, (type as JavaxTypeImpl).impl))
    }

    override fun getCollectionType(type: TypeLangModel): TypeLangModel {
        return JavaxTypeImpl(Utils.types.getDeclaredType(collectionElement, (type as JavaxTypeImpl).impl))
    }

    override val errorType: TypeLangModel
        get() = JavaxTypeImpl(ErrorTypeImpl)
}
