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

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtFieldBase

internal class KspFieldImpl(
    private val impl: KSPropertyDeclaration,
    override val owner: KspTypeDeclarationImpl,
    override val isStatic: Boolean,
    private val refinedOwner: KSType? = null,
) : CtFieldBase(), Annotated by KspAnnotatedImpl(impl) {

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val type: Type by lazy {
        val jvmSignatureHint = Utils.resolver.mapToJvmSignature(impl)
        if (refinedOwner != null) KspTypeImpl(
            impl = impl.asMemberOf(refinedOwner),
            jvmSignatureHint = jvmSignatureHint,
        ) else KspTypeImpl(
            reference = impl.type,
            jvmSignatureHint = jvmSignatureHint,
        )
    }
    override val name: String get() = impl.simpleName.asString()

    override val platformModel: KSPropertyDeclaration get() = impl
}