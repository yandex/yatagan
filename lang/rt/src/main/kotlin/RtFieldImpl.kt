package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.lang.common.FieldLangModelBase
import java.lang.reflect.Field

internal class RtFieldImpl(
    private val impl: Field,
    override val owner: RtTypeDeclarationImpl,
) : FieldLangModelBase(), AnnotatedLangModel by RtAnnotatedImpl(impl) {
    override val type: TypeLangModel by lazy {
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

    override val platformModel: Field
        get() = impl
}
