/*
 * Copyright 2026 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import com.yandex.yatagan.lang.compiled.CtConstructorBase
import com.yandex.yatagan.lang.compiled.CtFieldBase
import com.yandex.yatagan.lang.compiled.CtMethodBase
import com.yandex.yatagan.lang.compiled.CtParameterBase
import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass

internal class KcpConstructorImpl(
    private val impl: IrConstructor,
    override val constructee: KcpTypeDeclarationImpl,
) : CtConstructorBase(), LexicalScope by constructee {
    // Materialized once: annotations are queried constantly (scopes, qualifiers,
    // conditionals) and re-wrapping on every access dominates graph construction time.
    override val annotations: Sequence<CtAnnotationBase> by lazy {
        impl.annotations.map { KcpAnnotationImpl(this, it) }.asSequence()
    }

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

internal class KcpMethodImpl(
    private val impl: IrSimpleFunction,
    override val owner: KcpTypeDeclarationImpl,
    override val name: String,
    override val isStatic: Boolean,
) : CtMethodBase(), LexicalScope by owner {
    private val declaredIn
        get() = impl.parentAsClass

    // Materialized once: annotations are queried constantly (scopes, qualifiers,
    // conditionals) and re-wrapping on every access dominates graph construction time.
    override val annotations: Sequence<CtAnnotationBase> by lazy {
        impl.annotations.map { KcpAnnotationImpl(this, it) }.asSequence()
    }

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

internal class KcpParameterImpl(
    private val lexicalScope: LexicalScope,
    private val impl: IrValueParameter,
    private val implType: org.jetbrains.kotlin.ir.types.IrType,
) : CtParameterBase(), LexicalScope by lexicalScope {
    // Materialized once: annotations are queried constantly (scopes, qualifiers,
    // conditionals) and re-wrapping on every access dominates graph construction time.
    override val annotations: Sequence<CtAnnotationBase> by lazy {
        impl.annotations.map { KcpAnnotationImpl(this, it) }.asSequence()
    }

    override val name: String
        get() = impl.name.asString()

    override val type: Type by lazy { kcpType(implType, position = TypePosition.Parameter) }
}

internal class KcpFieldImpl(
    private val impl: IrField,
    override val owner: KcpTypeDeclarationImpl,
    override val isStatic: Boolean,
    private val declaredIn: org.jetbrains.kotlin.ir.declarations.IrClass,
    private val effectivelyPublic: Boolean = impl.isEffectivelyPublic,
) : CtFieldBase(), LexicalScope by owner {
    // Materialized once: annotations are queried constantly (scopes, qualifiers,
    // conditionals) and re-wrapping on every access dominates graph construction time.
    override val annotations: Sequence<CtAnnotationBase> by lazy {
        impl.annotations.map { KcpAnnotationImpl(this, it) }.asSequence()
    }

    override val isEffectivelyPublic: Boolean
        get() = effectivelyPublic

    override val type: Type by lazy { kcpType(owner.refine(impl.type, declaredIn)) }

    override val name: String
        get() = impl.name.asString()

    override val platformModel: IrFieldSymbol
        get() = impl.symbol
}

internal class KcpSyntheticFieldImpl(
    override val owner: TypeDeclaration,
    override val type: Type = owner.asType(),
    override val name: String,
) : CtFieldBase() {
    override val isEffectivelyPublic: Boolean
        get() = true

    override val annotations: Sequence<CtAnnotationBase>
        get() = emptySequence()

    override val platformModel: Any?
        get() = null

    override val isStatic: Boolean
        get() = true
}

internal val IrDeclarationWithVisibility.isEffectivelyPublic: Boolean
    get() = visibility == DescriptorVisibilities.PUBLIC || visibility == DescriptorVisibilities.INTERNAL

internal val IrDeclarationWithVisibility.isNonPrivate: Boolean
    get() = !DescriptorVisibilities.isPrivate(visibility)
