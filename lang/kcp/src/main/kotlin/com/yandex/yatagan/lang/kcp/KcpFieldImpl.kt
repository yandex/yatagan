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

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtFieldBase
import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol

internal class KcpFieldImpl(
    private val impl: IrField,
    override val owner: KcpTypeDeclarationImpl,
    override val isStatic: Boolean,
    private val declaredIn: IrClass,
    private val effectivelyPublic: Boolean = impl.isEffectivelyPublic,
) : CtFieldBase(), CtAnnotated by KcpAnnotatedImpl(owner, impl), LexicalScope by owner {
    override val isEffectivelyPublic: Boolean
        get() = effectivelyPublic

    override val type: Type by lazy { kcpType(owner.refine(impl.type, declaredIn)) }

    override val name: String
        get() = impl.name.asString()

    override val platformModel: IrFieldSymbol
        get() = impl.symbol
}
