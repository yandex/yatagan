package com.yandex.daggerlite.lang.ksp

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference

internal fun KSTypeReference.replaceType(type: KSType): KSTypeReference {
    data class MappedReference1(
        val original: KSTypeReference,
        val mappedType: KSType,
    ) : KSTypeReference by original {
        override fun resolve() = mappedType
    }

    return when (this) {
        is MappedReference1 -> MappedReference1(original = original, mappedType = type)
        else -> MappedReference1(original = this, mappedType = type)
    }
}

internal fun KSTypeReference.replaceType(typeReference: KSTypeReference): KSTypeReference {
    data class MappedReference2(
        val original: KSTypeReference,
        val mapped: KSTypeReference,
    ) : KSTypeReference by original {
        override fun resolve() = mapped.resolve()
    }

    return when (this) {
        is MappedReference2 -> MappedReference2(original = original, mapped = typeReference)
        else -> MappedReference2(original = this, mapped = typeReference)
    }
}

internal fun KSType.asReference(): KSTypeReference = Utils.resolver.createKSTypeReferenceFromKSType(this)

internal fun KSTypeReference?.resolveOrError(): KSType {
    return this?.resolve() ?: ErrorTypeImpl
}