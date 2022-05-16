package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFieldImpl private constructor(
    private val impl: KSPropertyDeclaration,
    override val owner: TypeDeclarationLangModel,
    override val isStatic: Boolean,
) : FieldLangModel {
    override val annotations: Sequence<AnnotationLangModel> = annotationsFrom(impl)

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val type: TypeLangModel by lazy(NONE) {
        KspTypeImpl(
            reference = impl.type,
            jvmSignatureHint = Utils.resolver.mapToJvmSignature(impl),
        )
    }
    override val name: String get() = impl.simpleName.asString()

    override val platformModel: KSPropertyDeclaration get() = impl

    override fun toString() = "$owner::$name: $type"

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