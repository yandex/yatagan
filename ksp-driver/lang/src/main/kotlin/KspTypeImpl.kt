package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspTypeImpl private constructor(
    val impl: KSType,
) : CtTypeLangModel() {

    override val name: CtTypeNameModel by lazy {
        CtTypeNameModel(impl)
    }

    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        // This is safe to cast to KSClassDeclaration here, as Kotlin models everything we need as class.
        KspTypeDeclarationImpl(impl.declaration as KSClassDeclaration)
    }

    override val typeArguments: List<TypeLangModel> by lazy(NONE) {
        impl.arguments.map {
            Factory(it.type!!.resolve())
        }
    }

    override val isBoolean: Boolean
        get() {
            @OptIn(KspExperimental::class)
            return impl.declaration == Utils.resolver.getJavaClassByName("java.lang.Boolean")
        }

    companion object Factory : ObjectCache<KSType, KspTypeImpl>() {
        operator fun invoke(
            impl: KSType,
            varianceAsWildcard: Boolean = false,
        ) = createCached(mapToJavaPlatformIfNeeded(
            type = impl,
            varianceAsWildcard = varianceAsWildcard,
        ), ::KspTypeImpl)
    }
}