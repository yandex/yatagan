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

import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtParameterBase

internal class KspParameterImpl(
    private val impl: KSValueParameter,
    private val refinedTypeRef: KSTypeReference,
    private val jvmSignatureSupplier: () -> String?,
) : CtParameterBase(), CtAnnotated by KspAnnotatedImpl(impl) {

    override val name: String
        get() = impl.name?.asString() ?: "unnamed"

    override val type: Type by lazy {
        KspTypeImpl(
            reference = refinedTypeRef,
            jvmSignatureHint = jvmSignatureSupplier(),
            typePosition = TypeMapCache.Position.Parameter,
        )
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is KspParameterImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()
}