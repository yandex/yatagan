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

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.yandex.yatagan.base.ifOrElseNull
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtMethodBase
import com.yandex.yatagan.lang.scope.LexicalScope

internal class KspMethodImpl(
    private val impl: KSFunctionDeclaration,
    override val owner: KspTypeDeclarationImpl,
    override val isStatic: Boolean,
) : CtMethodBase(), CtAnnotated by KspAnnotatedImpl(owner, impl), LexicalScope by owner {
    private val jvmSignature = JvmMethodSignature(impl)

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val isAbstract: Boolean
        get() = impl.isAbstract


    override val returnType: Type by lazy {
        var typeReference = impl.returnType
        if (!isStatic && typeReference != null) {
            // No need to resolve generics for static functions.
            val returnType = impl.asMemberOf(owner.type.impl).returnType
            typeReference = returnType?.let(typeReference::replaceType)
        }
        KspTypeImpl(
            reference = typeReference,
            jvmSignatureHint = jvmSignature.returnTypeSignature,
        )
    }

    override val name: String by lazy {
        Utils.resolver.getJvmName(impl) ?: impl.simpleName.asString()
    }

    override val parameters: Sequence<Parameter> = parametersSequenceFor(
        declaration = impl,
        containing = ifOrElseNull(!isStatic) { owner.type.impl },
        jvmMethodSignature = jvmSignature,
    )

    override val platformModel: KSFunctionDeclaration get() = impl
}