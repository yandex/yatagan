package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionImpl(
    override val owner: TypeDeclarationLangModel,
    override val impl: KSFunctionDeclaration,
    override val isConstructor: Boolean = false,
    override val isFromCompanionObject: Boolean = false,
) : KspAnnotatedImpl(), FunctionLangModel {
    override val isAbstract: Boolean
        get() = impl.isAbstract
    override val isStatic: Boolean
        get() = impl.isStatic
    override val returnType: TypeLangModel by lazy(NONE) {
        KspTypeImpl(impl.returnType!!.resolve())
    }
    override val name: String get() = impl.simpleName.asString()
    override val parameters: Sequence<ParameterLangModel> = impl.parameters
        .asSequence().map(::KspParameterImpl).memoize()
}