package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.NamedTypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspTypeImpl private constructor(
    private val impl: KSType,
) : NamedTypeLangModel() {
    override val name: ClassNameModel by lazy {
        ClassNameModel(impl)
    }

    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        // This is safe to cast to KSClassDeclaration here, as Kotlin models everything we need as class.
        KspTypeDeclarationImpl(impl.declaration as KSClassDeclaration)
    }

    override val typeArguments: List<TypeLangModel> by lazy(NONE) {
        impl.arguments.map { Factory(it.type!!.resolve()) }
    }

    override val isBoolean: Boolean
        get() = impl == Utils.resolver.builtIns.booleanType

    companion object Factory : ObjectCache<KSType, KspTypeImpl>() {
        operator fun invoke(impl: KSType) = createCached(impl.makeNotNullable(), ::KspTypeImpl)
    }
}