package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.lang.model.element.VariableElement
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxFieldImpl private constructor(
    override val owner: TypeDeclarationLangModel,
    private val impl: VariableElement,
) : FieldLangModel {
    override val isStatic: Boolean get() = impl.isStatic
    override val type: TypeLangModel by lazy(NONE) { JavaxTypeImpl(impl.asType()) }
    override val name: String get() = impl.simpleName.toString()

    companion object Factory : ObjectCache<VariableElement, JavaxFieldImpl>() {
        operator fun invoke(
            owner: TypeDeclarationLangModel,
            impl: VariableElement,
        ) = createCached(impl) { JavaxFieldImpl(owner, it) }
    }
}