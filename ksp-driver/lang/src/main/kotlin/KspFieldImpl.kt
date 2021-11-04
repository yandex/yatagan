package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFieldImpl(
    private val impl: KSPropertyDeclaration,
    override val owner: TypeDeclarationLangModel,
) : FieldLangModel {
    override val isStatic: Boolean get() = impl.isStatic
    override val type: TypeLangModel by lazy(NONE) { KspTypeImpl(impl.type.resolve()) }
    override val name: String get() = impl.simpleName.asString()
}