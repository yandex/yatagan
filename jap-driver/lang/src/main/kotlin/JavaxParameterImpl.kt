package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.lang.model.element.VariableElement

internal class JavaxParameterImpl(
    override val impl: VariableElement,
) : JavaxAnnotatedImpl(), ParameterLangModel {
    override val name: String
        get() = impl.simpleName.toString()
    override val type: TypeLangModel by lazy { NamedTypeLangModel(impl.asType()) }
}