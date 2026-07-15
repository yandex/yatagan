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
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtConstructorBase
import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass

internal class KcpConstructorImpl(
    private val impl: IrConstructor,
    override val constructee: KcpTypeDeclarationImpl,
) : CtConstructorBase(), CtAnnotated by KcpAnnotatedImpl(constructee, impl), LexicalScope by constructee {
    override val isEffectivelyPublic: Boolean
        get() = impl.isEffectivelyPublic

    override val parameters: Sequence<Parameter>
        get() = impl.nonDispatchParameters.asSequence().map { parameter ->
            KcpParameterImpl(
                lexicalScope = this,
                impl = parameter,
                implType = constructee.refine(parameter.type, impl.parentAsClass),
            )
        }

    override val platformModel: IrConstructorSymbol
        get() = impl.symbol
}
