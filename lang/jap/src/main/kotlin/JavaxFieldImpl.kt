package com.yandex.daggerlite.lang.jap

import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.compiled.CtAnnotated
import com.yandex.daggerlite.lang.compiled.CtFieldBase
import javax.lang.model.element.VariableElement

internal class JavaxFieldImpl (
    override val owner: JavaxTypeDeclarationImpl,
    private val impl: VariableElement,
) : CtFieldBase(), CtAnnotated by JavaxAnnotatedImpl(impl) {
    override val isStatic: Boolean get() = impl.isStatic

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val type: Type by lazy {
        JavaxTypeImpl(impl.asMemberOf(owner.type))
    }

    override val name: String get() = impl.simpleName.toString()

    override val platformModel: VariableElement get() = impl
}