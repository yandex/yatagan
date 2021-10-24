package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.NamedTypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspTypeImpl(
    private val impl: KSType,
) : NamedTypeLangModel() {
    override val name: ClassNameModel by lazy {
        ClassNameModel(impl)
    }

    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        // This is safe to cast to KSClassDeclaration here, as Kotlin models everything we need as class.
        KspTypeDeclarationImpl(impl.declaration as KSClassDeclaration)
    }

    override val typeArguments: Sequence<TypeLangModel> =
        impl.arguments.asSequence().map { KspTypeImpl(it.type!!.resolve()) }.memoize()
}