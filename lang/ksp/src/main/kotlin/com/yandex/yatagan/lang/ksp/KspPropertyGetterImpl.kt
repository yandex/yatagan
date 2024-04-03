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

import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type

internal class KspPropertyGetterImpl(
    getter: KSPropertyGetter,
    override val owner: KspTypeDeclarationImpl,
    isStatic: Boolean,
) : KspPropertyAccessorBase<KSPropertyGetter>(owner, getter, isStatic) {

    override val returnType: Type by lazy {
        var typeReference = property.type
        if (!isStatic) {
            typeReference = typeReference.replaceType(property.asMemberOf(owner.type.impl))
        }
        KspTypeImpl(
            reference = typeReference,
            jvmSignatureHint = jvmSignature,
        )
    }

    @Suppress("DEPRECATION")  // capitalize
    override val name: String by lazy {
        Utils.resolver.getJvmName(getter) ?: run {
            val propName = property.simpleName.asString()
            if (PropNameIsRegex.matches(propName)) propName
            else "get${propName.capitalize()}"
        }
    }

    override val parameters: Sequence<Parameter> = emptySequence()

    override val platformModel: Any?
        get() = null

    companion object {
        private val PropNameIsRegex = "^is[^a-z].*$".toRegex()
    }
}