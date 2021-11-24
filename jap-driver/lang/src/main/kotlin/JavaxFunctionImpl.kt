package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtFunctionLangModel
import javax.lang.model.element.ExecutableElement
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxFunctionImpl private constructor(
    private val impl: ExecutableElement,
    override val owner: TypeDeclarationLangModel,
    override val isFromCompanionObject: Boolean = false,
) : JavaxAnnotatedLangModel by JavaxAnnotatedImpl(impl), CtFunctionLangModel() {
    override val isAbstract: Boolean get() = impl.isAbstract
    override val isStatic: Boolean get() = impl.isStatic
    override val returnType: TypeLangModel by lazy(NONE) { JavaxTypeImpl(impl.returnType) }
    override val name: String get() = impl.simpleName.toString()
    override val parameters: Sequence<ParameterLangModel> =
        impl.parameters.asSequence().map(::JavaxParameterImpl).memoize()

    companion object Factory : ObjectCache<ExecutableElement, JavaxFunctionImpl>() {
        operator fun invoke(
            impl: ExecutableElement,
            owner: TypeDeclarationLangModel,
            isFromCompanionObject: Boolean = false,
        ) = createCached(impl) {
            JavaxFunctionImpl(
                impl = impl,
                owner = owner,
                isFromCompanionObject = isFromCompanionObject,
            )
        }
    }
}