package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.yandex.yatagan.base.ifOrElseNull
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtMethodBase

internal class KspMethodImpl(
    private val impl: KSFunctionDeclaration,
    override val owner: KspTypeDeclarationImpl,
    override val isStatic: Boolean,
) : CtMethodBase(), CtAnnotated by KspAnnotatedImpl(impl) {
    private val jvmSignature = JvmMethodSignature(impl)

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val isAbstract: Boolean
        get() = impl.isAbstract


    override val returnType: Type by lazy {
        var typeReference = impl.returnType ?: ErrorTypeImpl.asReference()
        if (!isStatic) {
            // No need to resolve generics for static functions.
            typeReference = typeReference.replaceType(impl.asMemberOf(owner.type.impl).returnType ?: ErrorTypeImpl)
        }
        KspTypeImpl(
            reference = typeReference,
            jvmSignatureHint = jvmSignature.returnTypeSignature,
        )
    }

    override val name: String by lazy {
        Utils.resolver.getJvmName(impl) ?: impl.simpleName.asString()
    }

    override val parameters: Sequence<Parameter> = parametersSequenceFor(
        declaration = impl,
        containing = ifOrElseNull(!isStatic) { owner.type.impl },
        jvmMethodSignature = jvmSignature,
    )

    override val platformModel: KSFunctionDeclaration get() = impl
}