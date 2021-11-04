package com.yandex.daggerlite.core.lang

/**
 * Models [com.yandex.daggerlite.Component] annotation.
 */
interface ComponentAnnotationLangModel {
    val isRoot: Boolean
    val modules: Sequence<TypeLangModel>
    val dependencies: Sequence<TypeLangModel>
    val variant: Sequence<TypeLangModel>
}