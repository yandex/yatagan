package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.lang.model.element.VariableElement
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxFieldImpl private constructor(
    override val owner: JavaxTypeDeclarationImpl,
    impl: VariableElement,
) : FieldLangModel, JavaxAnnotatedImpl<VariableElement>(impl) {
    override val isStatic: Boolean get() = impl.isStatic

    override val type: TypeLangModel by lazy(NONE) {
        JavaxTypeImpl(impl.asMemberOf(owner.type))
    }

    override val name: String get() = impl.simpleName.toString()

    override val platformModel: VariableElement get() = impl

    companion object Factory : BiObjectCache<JavaxTypeDeclarationImpl, VariableElement, JavaxFieldImpl>() {
        operator fun invoke(
            owner: JavaxTypeDeclarationImpl,
            impl: VariableElement,
        ) = createCached(owner, impl) {
            JavaxFieldImpl(
                owner = owner,
                impl = impl,
            )
        }
    }
}