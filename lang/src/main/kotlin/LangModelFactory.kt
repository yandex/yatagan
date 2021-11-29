package com.yandex.daggerlite.core.lang

interface LangModelFactory {
    fun getAnnotation(clazz: Class<out Annotation>): AnnotationLangModel
    fun getListType(type: TypeLangModel): TypeLangModel
}