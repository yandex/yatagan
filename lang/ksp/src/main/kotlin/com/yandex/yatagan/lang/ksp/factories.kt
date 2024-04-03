package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.scope.LexicalScope

@Suppress("FunctionName")
internal fun LexicalScope.KspTypeImpl(
    impl: KSType?,
    jvmSignatureHint: String? = null,
): Type = KspTypeImpl(KspTypeImpl.ResolveTypeInfo(
    type = impl,
    jvmSignatureHint = jvmSignatureHint,
))

@Suppress("FunctionName")
internal fun LexicalScope.KspTypeImpl(
    reference: KSTypeReference?,
    jvmSignatureHint: String? = null,
    typePosition: TypeMap.Position = TypeMap.Position.Other,
): Type = KspTypeImpl(KspTypeImpl.ResolveTypeInfo(
    reference = reference,
    jvmSignatureHint = jvmSignatureHint,
    typePosition = typePosition,
))
