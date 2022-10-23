package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.lang.Type

internal class CtComponentAnnotationImpl(
    impl: CtAnnotation,
) : ComponentAnnotationLangModel {
    override val isRoot: Boolean = impl.getBoolean("isRoot")
    override val modules: Sequence<Type> = impl.getTypes("modules")
    override val dependencies: Sequence<Type> = impl.getTypes("dependencies")
    override val variant: Sequence<Type> = impl.getTypes("variant")
    override val multiThreadAccess: Boolean = impl.getBoolean("multiThreadAccess")
}

