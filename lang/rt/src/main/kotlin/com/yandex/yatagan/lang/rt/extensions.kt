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

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.TypeDeclaration

/**
 * Convenience extension for creating [MethodSignatureEquivalenceWrapper] for the given [Method].
 */
fun ReflectMethod.signatureEquivalence() = MethodSignatureEquivalenceWrapper(this)

// region RT types accessors for `platformModel` property

val Method.rt
    get() = platformModel as ReflectMethod

val Field.rt
    get() = platformModel as ReflectField

val TypeDeclaration.rt
    get() = platformModel as Class<*>

val Constructor.rt
    get() = platformModel as ReflectConstructor

val Annotation.Value.rawValue: Any
    get() = checkNotNull(platformModel)

// endregion