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
) : FieldLangModel {
    override val annotations: Sequence<AnnotationLangModel> = annotationsFrom(impl)

    override val isStatic: Boolean get() = impl.isStatic
    override val type: TypeLangModel by lazy(NONE) {
        KspTypeImpl(
            impl = impl.type.resolve(),
            jvmSignatureHint = Utils.resolver.mapToJvmSignature(impl),
        )
    }
    override val name: String get() = impl.simpleName.asString()

    override val platformModel: KSPropertyDeclaration get() = impl

    companion object Factory : ObjectCache<KSPropertyDeclaration, KspFieldImpl>() {
        operator fun invoke(
            impl: KSPropertyDeclaration,
            owner: TypeDeclarationLangModel,
        ) = createCached(impl) {
            KspFieldImpl(impl, owner)
        }
    }
}