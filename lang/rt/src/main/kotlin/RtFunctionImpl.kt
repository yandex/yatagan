package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.IntoListAnnotationLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import java.lang.reflect.Method

internal class RtFunctionImpl(
    impl: Method,
    override val owner: RtTypeDeclarationImpl,
) : FunctionLangModel, RtAnnotatedImpl<Method>(impl) {
    private val parametersAnnotations by lazy { impl.parameterAnnotations }
    private val parametersTypes by lazy { impl.genericParameterTypes }
    private val parameterNames by lazy { impl.parameterNamesCompat() }

    override val parameters: Sequence<ParameterLangModel> by lazy {
        Array(impl.getParameterCountCompat(), ::ParameterImpl).asSequence()
    }

    override val returnType: TypeLangModel by lazy {
        RtTypeImpl(impl.genericReturnType.resolveGenericsHierarchyAware(
            declaringClass = impl.declaringClass,
            asMemberOf = owner,
        ))
    }

    override val propertyAccessorInfo: FunctionLangModel.PropertyAccessorInfo? by lazy {
        owner.findKotlinPropertyAccessorFor(impl)
    }

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val isStatic: Boolean
        get() = impl.isStatic

    override val isAbstract: Boolean
        get() = impl.isAbstract

    override val name: String
        get() = impl.name

    override val platformModel: Method get() = impl

    //region Annotations

    override val providesAnnotationIfPresent: ProvidesAnnotationLangModel? by lazy {
        impl.getAnnotation(Provides::class.java)?.let { RtProvidesAnnotationImpl(it) }
    }
    override val intoListAnnotationIfPresent: IntoListAnnotationLangModel? by lazy {
        impl.getAnnotation(IntoList::class.java)?.let { RtIntoListAnnotationImpl(it) }
    }

    //endregion

    override fun toString() = buildString {
        append(owner.qualifiedName)
        append("::")
        append(name).append('(')
        parameters.joinTo(this)
        append("): ")
        append(returnType)
    }

    private inner class ParameterImpl(
        val index: Int,
    ) : ParameterLangModel {
        override val annotations: Sequence<AnnotationLangModel> by lazy {
            parametersAnnotations[index].map { RtAnnotationImpl(it) }.asSequence()
        }

        override val name: String
            get() = parameterNames[index]

        override val type: TypeLangModel by lazy {
            RtTypeImpl(parametersTypes[index].resolveGenericsHierarchyAware(
                declaringClass = impl.declaringClass,
                asMemberOf = owner,
            ))
        }

        override fun toString() = "$name: $type"
    }

    companion object Factory : BiObjectCache<RtTypeDeclarationImpl, Method, RtFunctionImpl>() {
        operator fun invoke(
            method: Method,
            owner: RtTypeDeclarationImpl,
        ) = createCached(owner, method) {
            RtFunctionImpl(impl = method, owner = owner)
        }
    }
}
