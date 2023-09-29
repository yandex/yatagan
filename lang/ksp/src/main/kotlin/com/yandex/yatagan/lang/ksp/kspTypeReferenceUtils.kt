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

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference

internal fun KSTypeReference.replaceType(type: KSType): KSTypeReference {
    data class MappedReference1(
        val original: KSTypeReference,
        val mappedType: KSType,
    ) : KSTypeReference by original {
        override fun resolve() = mappedType
    }

    return when (this) {
        is MappedReference1 -> MappedReference1(original = original, mappedType = type)
        else -> MappedReference1(original = this, mappedType = type)
    }
}

internal fun KSTypeReference.replaceType(typeReference: KSTypeReference): KSTypeReference {
    data class MappedReference2(
        val original: KSTypeReference,
        val mapped: KSTypeReference,
    ) : KSTypeReference by original {
        override fun resolve() = mapped.resolve()
    }

    return when (this) {
        is MappedReference2 -> MappedReference2(original = original, mapped = typeReference)
        else -> MappedReference2(original = this, mapped = typeReference)
    }
}

internal fun KSType.asReference(): KSTypeReference = Utils.resolver.createKSTypeReferenceFromKSType(this)
