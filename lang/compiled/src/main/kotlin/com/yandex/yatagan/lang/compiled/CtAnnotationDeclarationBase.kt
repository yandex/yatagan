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

import com.yandex.yatagan.IntoMap
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.AnnotationDeclarationBase
import com.yandex.yatagan.lang.common.isDaggerCompat
import javax.inject.Qualifier
import javax.inject.Scope

abstract class CtAnnotationDeclarationBase : AnnotationDeclarationBase() {
    abstract override val annotations: Sequence<CtAnnotationBase>

    override fun <T : BuiltinAnnotation.OnAnnotationClass> getAnnotation(
        builtinAnnotation: BuiltinAnnotation.Target.OnAnnotationClass<T>,
    ): T? {
        val annotation: BuiltinAnnotation.OnAnnotationClass? = when(builtinAnnotation) {
            BuiltinAnnotation.IntoMap.Key -> (builtinAnnotation as BuiltinAnnotation.IntoMap.Key)
                .takeIf {
                    annotations.any { it.hasType<IntoMap.Key>() } ||
                            isDaggerCompat() && annotations.any { it.hasType(DaggerNames.MAP_KEY) }
                }
            BuiltinAnnotation.Qualifier -> (builtinAnnotation as BuiltinAnnotation.Qualifier)
                .takeIf { annotations.any { it.hasType<Qualifier>() } }
            BuiltinAnnotation.Scope -> (builtinAnnotation as BuiltinAnnotation.Scope)
                .takeIf { annotations.any { it.hasType<Scope>() } }
        }
        return builtinAnnotation.modelClass.cast(annotation)
    }
}