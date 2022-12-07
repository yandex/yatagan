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

import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.Modifier
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type

internal class KspPropertySetterImpl(
    private val setter: KSPropertySetter,
    override val owner: KspTypeDeclarationImpl,
    isStatic: Boolean,
) : KspPropertyAccessorBase<KSPropertySetter>(setter, isStatic) {

    override val isEffectivelyPublic: Boolean
        get() = super.isEffectivelyPublic && setter.modifiers.let {
            Modifier.PRIVATE !in it && Modifier.PROTECTED !in it
        }

    override val returnType: Type = KspTypeImpl(Utils.resolver.builtIns.unitType)

    @Suppress("DEPRECATION")  // capitalize
    override val name: String by lazy {
        Utils.resolver.getJvmName(setter) ?: "set${property.simpleName.asString().capitalize()}"
    }

    override val parameters: Sequence<Parameter> = sequence {
        yield(KspParameterImpl(
            impl = setter.parameter,
            refinedTypeRef = property.type.replaceType(property.asMemberOf(owner.type.impl)),
            jvmSignatureSupplier = { jvmSignature },
        ))
    }

    override val platformModel: Any?
        get() = null
}