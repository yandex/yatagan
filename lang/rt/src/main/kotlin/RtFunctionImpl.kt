package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.IntoSet
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.lang.AnnotatedLangModel
import com.yandex.daggerlite.lang.AnnotationLangModel
import com.yandex.daggerlite.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.lang.IntoCollectionAnnotationLangModel
import com.yandex.daggerlite.lang.Parameter
import com.yandex.daggerlite.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.common.FunctionLangModelBase
import com.yandex.daggerlite.lang.common.ParameterBase

internal class RtFunctionImpl(
    private val impl: ReflectMethod,
    override val owner: RtTypeDeclarationImpl,
) : FunctionLangModelBase(), AnnotatedLangModel by RtAnnotatedImpl(impl) {
    private val parametersAnnotations by lazy { impl.parameterAnnotations }
    private val parametersTypes by lazy { impl.genericParameterTypes }
    private val parameterNames by lazy { impl.parameterNamesCompat() }

    override val parameters: Sequence<Parameter> by lazy {
        Array(impl.getParameterCountCompat(), ::ParameterImpl).asSequence()
    }

    override val returnType: Type by lazy {
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

    override val platformModel: ReflectMethod get() = impl

    //region Annotations

    override val providesAnnotationIfPresent: ProvidesAnnotationLangModel?
        get() = impl.getAnnotation(Provides::class.java)?.let { RtProvidesAnnotationImpl(it) }

    override val intoListAnnotationIfPresent: IntoCollectionAnnotationLangModel?
        get() = impl.getAnnotation(IntoList::class.java)?.let { RtIntoListAnnotationImpl(it) }

    override val intoSetAnnotationIfPresent: IntoCollectionAnnotationLangModel?
        get() = impl.getAnnotation(IntoSet::class.java)?.let { RtIntoSetAnnotationImpl(it) }

    //endregion

    private inner class ParameterImpl(
        val index: Int,
    ) : ParameterBase() {
        override val annotations: Sequence<AnnotationLangModel> by lazy {
            parametersAnnotations[index].map { RtAnnotationImpl(it) }.asSequence()
        }

        override val name: String
            get() = parameterNames[index]

        override val type: Type by lazy {
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
