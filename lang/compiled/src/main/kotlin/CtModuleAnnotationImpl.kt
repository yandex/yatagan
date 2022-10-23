package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.lang.Type

internal class CtModuleAnnotationImpl(
    impl: CtAnnotation
) : ModuleAnnotationLangModel {
    override val includes: Sequence<Type> = impl.getTypes("includes")
    override val subcomponents: Sequence<Type> = impl.getTypes("subcomponents")
}