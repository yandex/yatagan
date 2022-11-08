package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtParameterBase

internal class KspParameterImpl(
    private val impl: KSValueParameter,
    private val refinedTypeRef: KSTypeReference,
    private val jvmSignatureSupplier: () -> String?,
) : CtParameterBase(), CtAnnotated by KspAnnotatedImpl(impl) {

    override val name: String
        get() = impl.name?.asString() ?: "unnamed"

    override val type: Type by lazy {
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
}