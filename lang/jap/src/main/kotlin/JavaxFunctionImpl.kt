package com.yandex.daggerlite.lang.jap

import com.yandex.daggerlite.lang.Parameter
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.compiled.CtAnnotatedLangModel
import com.yandex.daggerlite.lang.compiled.CtFunctionLangModel
import javax.lang.model.element.ExecutableElement

internal class JavaxFunctionImpl (
    override val owner: JavaxTypeDeclarationImpl,
    private val impl: ExecutableElement,
) : CtFunctionLangModel(), CtAnnotatedLangModel by JavaxAnnotatedImpl(impl) {

    override val isAbstract: Boolean get() = impl.isAbstract

    override val isStatic: Boolean get() = impl.isStatic

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val returnType: Type by lazy {
        JavaxTypeImpl(impl.asMemberOf(owner.type).asExecutableType().returnType)
    }
    override val name: String get() = impl.simpleName.toString()

    override val parameters: Sequence<Parameter> = parametersSequenceFor(impl, owner.type)

    override val platformModel: ExecutableElement get() = impl
}