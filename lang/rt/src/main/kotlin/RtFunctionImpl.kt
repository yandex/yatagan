package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.core.lang.IntoListAnnotationLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.lang.common.FunctionLangModelBase
import com.yandex.daggerlite.lang.common.ParameterLangModelBase
import java.lang.reflect.Method

internal class RtFunctionImpl(
    private val impl: Method,
    override val owner: RtTypeDeclarationImpl,
) : FunctionLangModelBase(), AnnotatedLangModel by RtAnnotatedImpl(impl) {
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

    private inner class ParameterImpl(
        val index: Int,
    ) : ParameterLangModelBase() {
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

        // region Annotations

        override val assistedAnnotationIfPresent: AssistedAnnotationLangModel?
            get() = parametersAnnotations[index]
                .find { it.javaAnnotationClass === Assisted::class.java }
                ?.let { RtAssistedAnnotationImpl(it as Assisted) }

        // endregion
    }
}
