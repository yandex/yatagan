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

package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.Binds
import com.yandex.yatagan.BindsInstance
import com.yandex.yatagan.IntoList
import com.yandex.yatagan.IntoMap
import com.yandex.yatagan.IntoSet
import com.yandex.yatagan.Multibinds
import com.yandex.yatagan.Provides
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.MethodBase
import javax.inject.Inject

abstract class CtMethodBase : MethodBase() {
    abstract override val annotations: Sequence<CtAnnotationBase>

    override fun <T : BuiltinAnnotation.OnMethod> getAnnotation(
        which: BuiltinAnnotation.Target.OnMethod<T>
    ): T? {
        val annotation: BuiltinAnnotation.OnMethod? = when (which) {
            BuiltinAnnotation.Binds -> (which as BuiltinAnnotation.Binds)
                .takeIf { annotations.any { it.hasType<Binds>() } }
            BuiltinAnnotation.BindsInstance -> (which as BuiltinAnnotation.BindsInstance)
                .takeIf { annotations.any { it.hasType<BindsInstance>() } }
            BuiltinAnnotation.Provides ->
                annotations.find { it.hasType<Provides>() }?.let { CtProvidesAnnotationImpl(it) }
            BuiltinAnnotation.IntoMap -> (which as BuiltinAnnotation.IntoMap)
                .takeIf { annotations.any { it.hasType<IntoMap>() } }
            BuiltinAnnotation.Multibinds -> (which as BuiltinAnnotation.Multibinds)
                .takeIf { annotations.any { it.hasType<Multibinds>() } }
            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject)
                .takeIf { annotations.any { it.hasType<Inject>() } }
        }
        return which.modelClass.cast(annotation)
    }

    override fun <T : BuiltinAnnotation.OnMethodRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnMethodRepeatable<T>
    ): List<T> {
        return when (which) {
            BuiltinAnnotation.IntoCollectionFamily -> buildList {
                for (annotation in annotations) {
                    when {
                        annotation.hasType<IntoList>() ->
                            add(which.modelClass.cast(CtIntoListAnnotationImpl(annotation)))
                        annotation.hasType<IntoSet>() ->
                            add(which.modelClass.cast(CtIntoSetAnnotationImpl(annotation)))
                    }
                }
            }
        }
    }
}