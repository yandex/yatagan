package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFieldImpl private constructor(
    private val impl: KSPropertyDeclaration,
) : FieldLangModel {
    override val owner: TypeDeclarationLangModel by lazy(NONE) {
        KspTypeDeclarationImpl(impl.parentDeclaration as KSClassDeclaration)
    }

    override val isStatic: Boolean get() = impl.isStatic
    override val type: TypeLangModel by lazy(NONE) { KspTypeImpl(impl.type.resolve()) }
    override val name: String get() = impl.simpleName.asString()

    companion object Factory : ObjectCache<KSPropertyDeclaration, KspFieldImpl>() {
        operator fun invoke(impl: KSPropertyDeclaration) = createCached(impl, ::KspFieldImpl)
    }
}