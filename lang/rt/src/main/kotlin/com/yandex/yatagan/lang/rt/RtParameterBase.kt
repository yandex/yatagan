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

import com.yandex.yatagan.Assisted
import com.yandex.yatagan.BindsInstance
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.ParameterBase
import com.yandex.yatagan.lang.scope.LexicalScope

internal abstract class RtParameterBase : ParameterBase(), LexicalScope {
    protected abstract val parameterAnnotations: Array<kotlin.Annotation>

    final override val annotations: Sequence<Annotation> by lazy {
        parameterAnnotations.map { RtAnnotationImpl(it) }.asSequence()
    }

    final override fun <T : BuiltinAnnotation.OnParameter> getAnnotation(
        which: BuiltinAnnotation.Target.OnParameter<T>,
    ): T? {
        val annotation: BuiltinAnnotation.OnParameter? = when (which) {
            BuiltinAnnotation.BindsInstance -> BuiltinAnnotation.BindsInstance.takeIf {
                parameterAnnotations.any { it is BindsInstance }
            }

            BuiltinAnnotation.Assisted -> run {
                for (annotation in parameterAnnotations)
                    if (annotation is Assisted)
                        return@run RtAssistedAnnotationImpl(annotation)
                null
            }
        }
        return which.modelClass.cast(annotation)
    }
}