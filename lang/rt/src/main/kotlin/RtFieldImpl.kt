package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.Annotated
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.common.FieldBase
import javax.inject.Inject

internal class RtFieldImpl(
    private val impl: ReflectField,
    override val owner: RtTypeDeclarationImpl,
) : FieldBase(), Annotated by RtAnnotatedImpl(impl) {
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

    override fun <T : BuiltinAnnotation.OnField> getAnnotation(
        which: BuiltinAnnotation.Target.OnField<T>
    ): T? {
        val value: BuiltinAnnotation.OnField? = when (which) {
            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject).takeIf {
                impl.isAnnotationPresent(Inject::class.java)
            }
        }

        @Suppress("UNCHECKED_CAST")
        return which.modelClass.cast(value) as T?
    }
}
