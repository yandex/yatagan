package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxParameterImpl(
    impl: VariableElement,
    refinedType: TypeMirror,
) : JavaxAnnotatedImpl<VariableElement>(impl), ParameterLangModel {
    override val name: String get() = impl.simpleName.toString()
    override val type: TypeLangModel by lazy(NONE) { JavaxTypeImpl(refinedType) }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is JavaxParameterImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()

    override fun toString() = "$name: $type"
}