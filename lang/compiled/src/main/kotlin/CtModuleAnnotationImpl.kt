package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.lang.TypeLangModel

internal class CtModuleAnnotationImpl(
    impl: CtAnnotationLangModel
) : ModuleAnnotationLangModel {
    override val includes: Sequence<TypeLangModel> = impl.getTypes("includes")
    override val subcomponents: Sequence<TypeLangModel> = impl.getTypes("subcomponents")
}