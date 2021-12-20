package com.yandex.daggerlite.core.lang

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

interface LangModelFactory {
    fun getAnnotation(clazz: Class<out Annotation>): AnnotationLangModel
    fun getListType(type: TypeLangModel): TypeLangModel
    fun getCollectionType(type: TypeLangModel): TypeLangModel

    companion object : LangModelFactory {
        @PublishedApi
        internal var delegate: LangModelFactory? = null

        inline fun use(factory: LangModelFactory, block: () -> Unit) {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            check(delegate == null)
            delegate = factory
            try {
                block()
            } finally {
                delegate = null
            }
        }

        override fun getAnnotation(clazz: Class<out Annotation>) = checkNotNull(delegate).getAnnotation(clazz)
        override fun getListType(type: TypeLangModel) = checkNotNull(delegate).getListType(type)
        override fun getCollectionType(type: TypeLangModel) = checkNotNull(delegate).getCollectionType(type)
    }
}