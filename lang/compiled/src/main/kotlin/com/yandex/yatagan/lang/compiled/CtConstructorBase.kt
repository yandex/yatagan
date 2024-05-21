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

import com.yandex.yatagan.AssistedInject
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.ConstructorBase
import com.yandex.yatagan.lang.common.isDaggerCompat
import javax.inject.Inject

abstract class CtConstructorBase : ConstructorBase() {
    override fun <T : BuiltinAnnotation.OnConstructor> getAnnotation(
        which: BuiltinAnnotation.Target.OnConstructor<T>,
    ): T? {
        val value: BuiltinAnnotation.OnConstructor? = when (which) {
            BuiltinAnnotation.AssistedInject -> {
                val daggerCompat = isDaggerCompat()
                (which as BuiltinAnnotation.AssistedInject)
                    .takeIf {
                        annotations.any { it.hasType<AssistedInject>() ||
                                daggerCompat && it.hasType(DaggerNames.ASSISTED_INJECT) }
                    }
            }

            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject)
                .takeIf { annotations.any { it.hasType<Inject>() } }
        }

        return which.modelClass.cast(value)
    }
}