package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.yandex.daggerlite.generator.lang.CtFunctionLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal abstract class KspFunctionPropertyAccessorBase<T : KSPropertyAccessor>(
    accessor: T,
    final override val isStatic: Boolean,
) : CtFunctionLangModel() {
    protected val property = accessor.receiver

    init {
        require(!property.isKotlinField()) { "Not reached: field can't be modeled as a property"}
    }

    protected val jvmSignature by lazy(NONE) {
        Utils.resolver.mapToJvmSignature(property)
    }

    override val isEffectivelyPublic: Boolean
        get() = property.isPublicOrInternal()

    // NOTE: We can't use annotations from |property| as they aren't properly accessible from Kapt.
    //  See https://youtrack.jetbrains.com/issue/KT-34684
    final override val annotations = annotationsFrom(accessor)

    final override val isAbstract: Boolean
        get() = property.isAbstract()
}