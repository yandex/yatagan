package com.yandex.daggerlite.lang.ksp

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.yandex.daggerlite.base.ifOrElseNull
import com.yandex.daggerlite.lang.Parameter
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.compiled.CtAnnotated
import com.yandex.daggerlite.lang.compiled.CtMethodBase

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