package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.yandex.daggerlite.generator.lang.CtAnnotatedLangModel
import com.yandex.daggerlite.generator.lang.CtFunctionLangModel

internal abstract class KspFunctionPropertyAccessorBase<T : KSPropertyAccessor>(
    private val accessor: T,
    final override val isStatic: Boolean,
) : CtFunctionLangModel(),
    // NOTE: We can't use annotations from |property| as they aren't properly accessible from Kapt.
    //  See https://youtrack.jetbrains.com/issue/KT-34684
    CtAnnotatedLangModel by KspAnnotatedImpl(accessor) {

    protected val property = accessor.receiver

    init {
        require(!property.isKotlinFieldInObject()) { "Not reached: field can't be modeled as a property" }
    }

    protected val jvmSignature by lazy {
        Utils.resolver.mapToJvmSignature(property)
    }

    override val isEffectivelyPublic: Boolean
        get() = property.isPublicOrInternal()

    final override val isAbstract: Boolean
        get() = property.isAbstract()
}