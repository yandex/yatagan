package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.IntoMap
import com.yandex.daggerlite.IntoSet
import com.yandex.daggerlite.Multibinds
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.lang.Annotated
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.Parameter
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.common.MethodBase
import javax.inject.Inject

internal class RtMethodImpl(
    private val impl: ReflectMethod,
    override val owner: RtTypeDeclarationImpl,
) : MethodBase(), Annotated by RtAnnotatedImpl(impl) {
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

    override fun <T : BuiltinAnnotation.OnMethod> getAnnotation(
        which: BuiltinAnnotation.Target.OnMethod<T>,
    ): T? {
        val annotation: BuiltinAnnotation.OnMethod? = when (which) {
            BuiltinAnnotation.Binds -> (which as BuiltinAnnotation.Binds)
                .takeIf { impl.isAnnotationPresent(Binds::class.java) }
            BuiltinAnnotation.BindsInstance -> (which as BuiltinAnnotation.BindsInstance)
                .takeIf { impl.isAnnotationPresent(BindsInstance::class.java) }
            BuiltinAnnotation.Provides ->
                impl.getAnnotation(Provides::class.java)?.let { RtProvidesAnnotationImpl(it) }
            BuiltinAnnotation.IntoMap -> (which as BuiltinAnnotation.IntoMap)
                .takeIf { impl.isAnnotationPresent(IntoMap::class.java) }
            BuiltinAnnotation.Multibinds -> (which as BuiltinAnnotation.Multibinds)
                .takeIf { impl.isAnnotationPresent(Multibinds::class.java) }
            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject)
                .takeIf { impl.isAnnotationPresent(Inject::class.java) }
        }
        return which.modelClass.cast(annotation)
    }

    override fun <T : BuiltinAnnotation.OnMethodRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnMethodRepeatable<T>,
    ): List<T> {
        return when (which) {
            BuiltinAnnotation.IntoCollectionFamily -> {
                impl.declaredAnnotations.mapNotNull {
                    when (it) {
                        is IntoList -> which.modelClass.cast(RtIntoListAnnotationImpl(it))
                        is IntoSet -> which.modelClass.cast(RtIntoSetAnnotationImpl(it))
                        else -> null
                    }
                }
            }
        }
    }

    private inner class ParameterImpl(
        val index: Int,
    ) : RtParameterBase() {
        override val parameterAnnotations: Array<kotlin.Annotation>
            get() = parametersAnnotations[index]

        override val name: String
            get() = parameterNames[index]

        override val type: Type by lazy {
            RtTypeImpl(parametersTypes[index].resolveGenericsHierarchyAware(
                declaringClass = impl.declaringClass,
                asMemberOf = owner,
            ))
        }
    }
}
