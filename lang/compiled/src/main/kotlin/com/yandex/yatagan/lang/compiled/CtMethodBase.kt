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
import com.yandex.yatagan.Conditional
import com.yandex.yatagan.Conditionals
import com.yandex.yatagan.ConditionsApi
import com.yandex.yatagan.IntoList
import com.yandex.yatagan.IntoMap
import com.yandex.yatagan.IntoSet
import com.yandex.yatagan.Multibinds
import com.yandex.yatagan.Provides
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.MethodBase
import com.yandex.yatagan.lang.common.isDaggerCompat
import javax.inject.Inject

abstract class CtMethodBase : MethodBase() {
    abstract override val annotations: Sequence<CtAnnotationBase>

    override fun <T : BuiltinAnnotation.OnMethod> getAnnotation(
        which: BuiltinAnnotation.Target.OnMethod<T>
    ): T? {
        val daggerCompat = isDaggerCompat()
        val annotation: BuiltinAnnotation.OnMethod? = when (which) {
            BuiltinAnnotation.Binds -> (which as BuiltinAnnotation.Binds)
                .takeIf {
                    annotations.any { it.hasType<Binds>() || daggerCompat && it.hasType(DaggerNames.BINDS) }
                }
            BuiltinAnnotation.BindsInstance -> (which as BuiltinAnnotation.BindsInstance)
                .takeIf {
                    annotations.any { it.hasType<BindsInstance>() ||
                            daggerCompat && it.hasType(DaggerNames.BINDS_INSTANCE) }
                }
            BuiltinAnnotation.Provides -> (which as BuiltinAnnotation.Provides)
                .takeIf {
                    annotations.any { it.hasType<Provides>() || daggerCompat && it.hasType(DaggerNames.PROVIDES) }
                }
            BuiltinAnnotation.IntoMap -> (which as BuiltinAnnotation.IntoMap)
                .takeIf {
                    annotations.any { it.hasType<IntoMap>() || daggerCompat && it.hasType(DaggerNames.INTO_MAP) }
                }
            BuiltinAnnotation.Multibinds -> (which as BuiltinAnnotation.Multibinds)
                .takeIf {
                    annotations.any { it.hasType<Multibinds>() ||
                            daggerCompat && it.hasType(DaggerNames.MULTIBINDS) }
                }
            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject)
                .takeIf { annotations.any { it.hasType<Inject>() } }
        }
        return which.modelClass.cast(annotation)
    }

    @OptIn(ConditionsApi::class)
    override fun <T : BuiltinAnnotation.OnMethodRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnMethodRepeatable<T>
    ): List<T> {
        return when (which) {
            BuiltinAnnotation.IntoCollectionFamily -> buildList {
                for (annotation in annotations) {
                    val daggerCompat = isDaggerCompat()
                    when {
                        annotation.hasType<IntoList>() ->
                            add(which.modelClass.cast(CtIntoListAnnotationImpl(annotation)))
                        annotation.hasType<IntoSet>() ->
                            add(which.modelClass.cast(CtIntoSetAnnotationImpl(annotation)))
                        daggerCompat && annotation.hasType(DaggerNames.INTO_SET) ->
                            add(which.modelClass.cast(CtIntoSetAnnotationDaggerCompatImpl(annotation)))
                        daggerCompat && annotation.hasType(DaggerNames.ELEMENTS_INTO_SET) ->
                            add(which.modelClass.cast(CtElementsIntoSetAnnotationDaggerCompatImpl(annotation)))
                    }
                }
            }
            BuiltinAnnotation.Conditional -> buildList {
                for (annotation in annotations) {
                    when {
                        annotation.hasType<Conditional>() ->
                            add(which.modelClass.cast(CtConditionalAnnotationImpl(annotation)))
                        annotation.hasType<Conditionals>() -> for (contained in annotation.getAnnotations("value"))
                            add(which.modelClass.cast(CtConditionalAnnotationImpl(contained)))
                    }
                }
            }
        }
    }
}