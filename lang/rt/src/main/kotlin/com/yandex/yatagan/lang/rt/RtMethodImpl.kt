/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.Binds
import com.yandex.yatagan.BindsInstance
import com.yandex.yatagan.IntoList
import com.yandex.yatagan.IntoMap
import com.yandex.yatagan.IntoSet
import com.yandex.yatagan.Multibinds
import com.yandex.yatagan.Provides
import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.common.MethodBase
import com.yandex.yatagan.lang.scope.LexicalScope
import javax.inject.Inject

internal class RtMethodImpl(
    private val impl: ReflectMethod,
    override val owner: RtTypeDeclarationImpl,
) : MethodBase(), Annotated by RtAnnotatedImpl(owner, impl), LexicalScope by owner {
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
    ) : RtParameterBase(), LexicalScope by this {
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
