package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.lang.common.FieldLangModelBase

internal class KspFieldImpl(
    private val impl: KSPropertyDeclaration,
    override val owner: KspTypeDeclarationImpl,
    override val isStatic: Boolean,
    private val refinedOwner: KSType? = null,
) : FieldLangModelBase(), AnnotatedLangModel by KspAnnotatedImpl(impl) {

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val type: TypeLangModel by lazy {
        val jvmSignatureHint = Utils.resolver.mapToJvmSignature(impl)
        if (refinedOwner != null) KspTypeImpl(
            impl = impl.asMemberOf(refinedOwner),
            jvmSignatureHint = jvmSignatureHint,
        ) else KspTypeImpl(
            reference = impl.type,
            jvmSignatureHint = jvmSignatureHint,
        )
    }
    override val name: String get() = impl.simpleName.asString()

    override val platformModel: KSPropertyDeclaration get() = impl
}