package com.yandex.daggerlite.core.lang

/**
 * Models [com.yandex.daggerlite.Module] annotation.
 */
interface ModuleAnnotationLangModel {
    val includes: Sequence<TypeLangModel>
    val subcomponents: Sequence<TypeLangModel>
}