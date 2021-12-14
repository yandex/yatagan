package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import com.yandex.daggerlite.generator.lang.CtFunctionLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionImpl private constructor(
    private val impl: KSFunctionDeclaration,
    override val owner: KspTypeDeclarationImpl,
) : CtFunctionLangModel() {
    private val jvmSignature = JvmMethodSignature(impl)

    override val annotations: Sequence<CtAnnotationLangModel> = annotationsFrom(impl)

    override val isAbstract: Boolean
        get() = impl.isAbstract

    override val isStatic: Boolean
        get() = impl.isStatic

    override val returnType: TypeLangModel by lazy(NONE) {
        KspTypeImpl(
            impl = impl.asMemberOf(owner.type).returnType ?: ErrorTypeImpl,
            jvmSignatureHint = jvmSignature.returnType,
        )
    }

    override val propertyAccessorInfo: Nothing? get() = null

    override val name: String by lazy(NONE) {
        Utils.resolver.getJvmName(impl) ?: impl.simpleName.asString()
    }

    override val parameters: Sequence<ParameterLangModel> = parametersSequenceFor(
        declaration = impl,
        containing = owner.type,
        jvmMethodSignature = jvmSignature,
    )

    companion object Factory : BiObjectCache<KSFunctionDeclaration, KspTypeDeclarationImpl, KspFunctionImpl>() {
        operator fun invoke(
            impl: KSFunctionDeclaration,
            owner: KspTypeDeclarationImpl,
        ) = createCached(impl, owner) {
            KspFunctionImpl(
                impl = impl,
                owner = owner,
            )
        }
    }
}