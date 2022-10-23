package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.lang.TypeLangModel

internal class CtComponentAnnotationImpl(
    impl: CtAnnotationLangModel,
) : ComponentAnnotationLangModel {
    override val isRoot: Boolean = impl.getBoolean("isRoot")
    override val modules: Sequence<TypeLangModel> = impl.getTypes("modules")
    override val dependencies: Sequence<TypeLangModel> = impl.getTypes("dependencies")
    override val variant: Sequence<TypeLangModel> = impl.getTypes("variant")
    override val multiThreadAccess: Boolean = impl.getBoolean("multiThreadAccess")
}

