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

@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtMethodBase
import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass

internal class KcpMethodImpl(
    private val impl: IrSimpleFunction,
    override val owner: KcpTypeDeclarationImpl,
    override val name: String,
    override val isStatic: Boolean,
) : CtMethodBase(), CtAnnotated by KcpAnnotatedImpl(owner, impl), LexicalScope by owner {
    private val declaredIn
        get() = impl.parentAsClass

    override val isEffectivelyPublic: Boolean
        get() = impl.isEffectivelyPublic

    override val isAbstract: Boolean
        get() = impl.modality == Modality.ABSTRACT

    override val returnType: Type by lazy {
        kcpType(owner.refine(impl.returnType, declaredIn))
    }

    override val parameters: Sequence<Parameter>
        get() = impl.nonDispatchParameters.asSequence().map { parameter ->
            KcpParameterImpl(
                lexicalScope = this,
                impl = parameter,
                implType = owner.refine(parameter.type, declaredIn),
            )
        }

    override val platformModel: IrSimpleFunctionSymbol
        get() = impl.symbol
}
