package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.yandex.daggerlite.generator.lang.CtFunctionLangModel

internal abstract class KspFunctionPropertyAccessorBase<T : KSPropertyAccessor>(
    private val accessor: T,
) : CtFunctionLangModel() {
    protected val property = accessor.receiver

    // NOTE: We can't use annotations from |impl| as they aren't properly accessible from Kapt.
    //  See https://youtrack.jetbrains.com/issue/KT-34684
    override val annotations = annotationsFrom(accessor)

    override val isAbstract: Boolean
        get() = property.isAbstract()

    override val isStatic: Boolean
        get() = property.isAbstract() || accessor.isAnnotationPresent<JvmStatic>()
}