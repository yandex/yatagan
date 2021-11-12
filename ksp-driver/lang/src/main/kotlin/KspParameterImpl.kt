package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSValueParameter
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspParameterImpl(
    private val impl: KSValueParameter
) : ParameterLangModel {
    override val annotations: Sequence<AnnotationLangModel> = annotationsFrom(impl)
    override val name: String
        get() = impl.name?.asString() ?: "unnamed"
    override val type: TypeLangModel by lazy(NONE) {
        KspTypeImpl(
            impl = impl.type.resolve(),
            // In parameter position, kotlin declaration-/use-site variance is mapped to a Java's wildcard type.
            varianceAsWildcard = true,
        )
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is KspParameterImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()
}