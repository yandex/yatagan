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

package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtMethodBase
import com.yandex.yatagan.lang.scope.LexicalScope

internal abstract class KspPropertyAccessorBase<T : KSPropertyAccessor>(
    lexicalScope: LexicalScope,
    private val accessor: T,
    final override val isStatic: Boolean,
) : CtMethodBase(),
    // NOTE: We can't use annotations from |property| as they aren't properly accessible from Kapt.
    //  See https://youtrack.jetbrains.com/issue/KT-34684
    CtAnnotated by KspAnnotatedImpl(lexicalScope, accessor), LexicalScope by lexicalScope {

    protected val property = accessor.receiver

    init {
        require(!property.isKotlinFieldInObject()) { "Not reached: field can't be modeled as a property" }
    }

    protected val jvmSignature by lazy {
        Utils.resolver.mapToJvmSignature(property)
    }

    override val isEffectivelyPublic: Boolean
        get() = property.isPublicOrInternal()

    final override val isAbstract: Boolean
        get() = property.isAbstract()
}