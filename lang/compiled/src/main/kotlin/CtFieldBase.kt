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

import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.FieldBase
import javax.inject.Inject

abstract class CtFieldBase : FieldBase() {
    override fun <T : BuiltinAnnotation.OnField> getAnnotation(
        which: BuiltinAnnotation.Target.OnField<T>,
    ): T? {
        val value: BuiltinAnnotation.OnField? = when(which) {
            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject).takeIf {
                annotations.any { it.hasType<Inject>() }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return which.modelClass.cast(value) as T?
    }
}