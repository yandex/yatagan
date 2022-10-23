package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.AnnotatedLangModel
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.common.FieldLangModelBase

internal class RtFieldImpl(
    private val impl: ReflectField,
    override val owner: RtTypeDeclarationImpl,
) : FieldLangModelBase(), AnnotatedLangModel by RtAnnotatedImpl(impl) {
    override val type: Type by lazy {
        RtTypeImpl(impl.genericType.resolveGenericsHierarchyAware(
            declaringClass = impl.declaringClass,
            asMemberOf = owner,
        ))
    }

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val isStatic: Boolean
        get() = impl.isStatic

    override val name: String
        get() = impl.name

    override val platformModel: ReflectField
        get() = impl
}
