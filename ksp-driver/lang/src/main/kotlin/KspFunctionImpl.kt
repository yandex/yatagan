package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionImpl private constructor(
    private val impl: KSFunctionDeclaration,
    override val owner: TypeDeclarationLangModel,
    override val isFromCompanionObject: Boolean,
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

    override val isConstructor: Boolean
        get() = impl.isConstructor()

    companion object Factory : ObjectCache<KSFunctionDeclaration, KspFunctionImpl>() {
        operator fun invoke(
            impl: KSFunctionDeclaration,
            owner: TypeDeclarationLangModel,
            isFromCompanionObject: Boolean = false,
        ) = createCached(impl) {
            KspFunctionImpl(
                impl = impl,
                owner = owner,
                isFromCompanionObject = isFromCompanionObject,
            )
        }
    }
}