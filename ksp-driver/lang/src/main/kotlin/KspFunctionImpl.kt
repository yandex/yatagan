package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionImpl(
    private val impl: KSFunctionDeclaration,
    override val owner: TypeDeclarationLangModel,
    override val isConstructor: Boolean = false,
    override val isFromCompanionObject: Boolean = false,
) : FunctionLangModel {
    override val annotations: Sequence<AnnotationLangModel> = annotationsFrom(impl)
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

    override fun equals(other: Any?): Boolean {
        return this === other || (other is KspFunctionImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()
}