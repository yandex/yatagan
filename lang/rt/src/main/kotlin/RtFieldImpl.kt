package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import java.lang.reflect.Field

internal class RtFieldImpl(
    impl: Field,
    override val owner: RtTypeDeclarationImpl,
) : FieldLangModel, RtAnnotatedImpl<Field>(impl) {
    override val type: TypeLangModel by lazy {
        RtTypeImpl(impl.genericType.resolve(asMemberOf = owner.type.impl))
    }

    override val isStatic: Boolean
        get() = impl.isStatic

    override val name: String
        get() = impl.name

    override val platformModel: Field
        get() = impl
}
