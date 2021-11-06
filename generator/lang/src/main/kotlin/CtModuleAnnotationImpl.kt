package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

internal class CtModuleAnnotationImpl(
    impl: CtAnnotationLangModel
) : ModuleAnnotationLangModel {
    override val includes: Sequence<TypeLangModel> = impl.getTypes("includes")
    override val subcomponents: Sequence<TypeLangModel> = impl.getTypes("subcomponents")
    override val bootstrap: Sequence<TypeLangModel> = impl.getTypes("bootstrap")
}