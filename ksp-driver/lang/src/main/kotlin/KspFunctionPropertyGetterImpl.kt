package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionPropertyGetterImpl(
    override val owner: TypeDeclarationLangModel,
    override val impl: KSPropertyDeclaration,
    override val isFromCompanionObject: Boolean,
) : KspAnnotatedImpl(), FunctionLangModel {
    override val isAbstract: Boolean
        get() = impl.isAbstract()
    override val isStatic: Boolean
        get() = impl.isStatic
    override val returnType: TypeLangModel by lazy(NONE) {
        KspTypeImpl(impl.type.resolve())
    }
    override val name: String
        @Suppress("DEPRECATION")
        get() = "get${impl.simpleName.asString().capitalize()}"
    override val parameters: Sequence<ParameterLangModel> = emptySequence()
    override val isConstructor: Boolean
        get() = false
}