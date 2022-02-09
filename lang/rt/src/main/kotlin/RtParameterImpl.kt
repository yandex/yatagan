package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import java.lang.reflect.Parameter
import java.lang.reflect.Type

internal class RtParameterImpl(
    impl: Parameter,
    refinedType: Type? = null,
) : ParameterLangModel, RtAnnotatedImpl<Parameter>(impl) {
    override val name: String
        get() = impl.name

    override val type: TypeLangModel by lazy {
        RtTypeImpl(refinedType ?: impl.parameterizedType)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RtParameterImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()

    override fun toString() = "$name: $type"
}