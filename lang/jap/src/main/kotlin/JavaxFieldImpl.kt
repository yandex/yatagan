package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotatedLangModel
import com.yandex.daggerlite.lang.common.FieldLangModelBase
import javax.lang.model.element.VariableElement

internal class JavaxFieldImpl (
    override val owner: JavaxTypeDeclarationImpl,
    private val impl: VariableElement,
) : FieldLangModelBase(), CtAnnotatedLangModel by JavaxAnnotatedImpl(impl) {
    override val isStatic: Boolean get() = impl.isStatic

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val type: TypeLangModel by lazy {
        JavaxTypeImpl(impl.asMemberOf(owner.type))
    }

    override val name: String get() = impl.simpleName.toString()

    override val platformModel: VariableElement get() = impl
}