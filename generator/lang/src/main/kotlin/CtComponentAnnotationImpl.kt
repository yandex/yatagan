package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

internal class CtComponentAnnotationImpl(
    impl: CtAnnotationLangModel,
) : ComponentAnnotationLangModel {
    override val isRoot: Boolean = impl.getBoolean("isRoot")
    override val modules: Sequence<TypeLangModel> = impl.getTypes("modules")
    override val dependencies: Sequence<TypeLangModel> = impl.getTypes("dependencies")
    override val variant: Sequence<TypeLangModel> = impl.getTypes("variant")
}

