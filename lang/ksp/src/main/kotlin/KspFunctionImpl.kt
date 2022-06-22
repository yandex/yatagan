package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.yandex.daggerlite.base.ifOrElseNull
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotatedLangModel
import com.yandex.daggerlite.generator.lang.CtFunctionLangModel

internal class KspFunctionImpl(
    private val impl: KSFunctionDeclaration,
    override val owner: KspTypeDeclarationImpl,
    override val isStatic: Boolean,
) : CtFunctionLangModel(), CtAnnotatedLangModel by KspAnnotatedImpl(impl) {
    private val jvmSignature = JvmMethodSignature(impl)

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val isAbstract: Boolean
        get() = impl.isAbstract


    override val returnType: TypeLangModel by lazy {
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

    override val parameters: Sequence<ParameterLangModel> = parametersSequenceFor(
        declaration = impl,
        containing = ifOrElseNull(!isStatic) { owner.type.impl },
        jvmMethodSignature = jvmSignature,
    )

    override val platformModel: KSFunctionDeclaration get() = impl
}