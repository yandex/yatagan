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

import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import com.yandex.yatagan.lang.scope.LexicalScope

internal class KspAnnotatedImpl<T : KSAnnotated>(
    private val lexicalScope: LexicalScope,
    val impl: T
) : CtAnnotated {
    override val annotations: Sequence<CtAnnotationBase> by lazy {
        impl.annotations.map { KspAnnotationImpl(lexicalScope, it) }.memoize()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}