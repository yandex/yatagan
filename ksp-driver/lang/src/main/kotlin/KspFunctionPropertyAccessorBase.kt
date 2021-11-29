package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.yandex.daggerlite.core.lang.FunctionLangModel.PropertyAccessorInfo
import com.yandex.daggerlite.generator.lang.CtFunctionLangModel

internal abstract class KspFunctionPropertyAccessorBase<T : KSPropertyAccessor>(
    private val accessor: T,
) : CtFunctionLangModel(), PropertyAccessorInfo {
    protected val property = accessor.receiver

    // NOTE: We can't use annotations from |property| as they aren't properly accessible from Kapt.
    //  See https://youtrack.jetbrains.com/issue/KT-34684
    final override val annotations = annotationsFrom(accessor)

    final override val isAbstract: Boolean
        get() = property.isAbstract()

    final override val isStatic: Boolean
        get() = property.isAbstract() || accessor.isAnnotationPresent<JvmStatic>()

    final override val propertyAccessorInfo: PropertyAccessorInfo
        get() = this

    final override val propertyName: String
        get() = property.simpleName.asString()
}