package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotatedLangModel
import com.yandex.daggerlite.generator.lang.CtParameterLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspParameterImpl(
    private val impl: KSValueParameter,
    private val refinedTypeRef: KSTypeReference,
    private val jvmSignatureSupplier: () -> String?,
) : CtParameterLangModel(), CtAnnotatedLangModel by KspAnnotatedImpl(impl) {

    override val name: String
        get() = impl.name?.asString() ?: "unnamed"
    override val type: TypeLangModel by lazy(NONE) {
        KspTypeImpl(
            reference = refinedTypeRef,
            jvmSignatureHint = jvmSignatureSupplier(),
            typePosition = TypeMapCache.Position.Parameter,
        )
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is KspParameterImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()

    override fun toString() = "$name: $type"
}