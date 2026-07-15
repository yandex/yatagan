/*
 * Copyright 2026 Yandex LLC
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

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer

internal class KcpAnnotatedImpl(
    private val lexicalScope: LexicalScope,
    private val impl: IrAnnotationContainer,
) : CtAnnotated {
    // Materialized once: annotations are queried constantly (scopes, qualifiers,
    // conditionals) and re-wrapping on every access dominates graph construction time.
    override val annotations: Sequence<CtAnnotationBase> by lazy {
        impl.annotations.map { KcpAnnotationImpl(lexicalScope, it) }.asSequence()
    }
}
