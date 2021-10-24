package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSValueParameter
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

class KspParameterImpl(
    override val impl: KSValueParameter
) : KspAnnotatedImpl(), ParameterLangModel {
    override val name: String
        get() = impl.name?.asString() ?: "unnamed"
    override val type: TypeLangModel by lazy(NONE) {
        KspTypeImpl(impl.type.resolve())
    }
}