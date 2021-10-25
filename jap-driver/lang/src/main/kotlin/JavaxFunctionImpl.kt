package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import javax.lang.model.element.ExecutableElement
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxFunctionImpl(
    override val owner: TypeDeclarationLangModel,
    override val impl: ExecutableElement,
    override val isConstructor: Boolean = false,
) : JavaxAnnotatedImpl(), FunctionLangModel {
    override val isAbstract: Boolean get() = impl.isAbstract
    override val isStatic: Boolean get() = impl.isStatic
    override val returnType: TypeLangModel by lazy(NONE) { NamedTypeLangModel(impl.returnType) }
    override val name: String get() = impl.simpleName.toString()
    override val parameters: Sequence<ParameterLangModel> =
        impl.parameters.asSequence().map(::JavaxParameterImpl).memoize()
}