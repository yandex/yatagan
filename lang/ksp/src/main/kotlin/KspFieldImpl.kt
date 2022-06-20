package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.lang.common.FieldLangModelBase

internal class KspFieldImpl private constructor(
    private val impl: KSPropertyDeclaration,
    override val owner: TypeDeclarationLangModel,
    override val isStatic: Boolean,
) : FieldLangModelBase(), AnnotatedLangModel by KspAnnotatedImpl(impl) {

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val type: TypeLangModel by lazy {
        KspTypeImpl(
            reference = impl.type,
            jvmSignatureHint = Utils.resolver.mapToJvmSignature(impl),
        )
    }
    override val name: String get() = impl.simpleName.asString()

    override val platformModel: KSPropertyDeclaration get() = impl

    companion object Factory : ObjectCache<KSPropertyDeclaration, KspFieldImpl>() {
        operator fun invoke(
            impl: KSPropertyDeclaration,
            owner: TypeDeclarationLangModel,
            isStatic: Boolean,
        ) = createCached(impl) {
            KspFieldImpl(
                impl = impl,
                owner = owner,
                isStatic = isStatic,
            )
        }
    }
}