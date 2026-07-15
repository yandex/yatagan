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

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.NoDeclaration
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.CtNamedType
import com.yandex.yatagan.lang.compiled.CtTypeBase
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import com.yandex.yatagan.lang.compiled.WildcardNameModel
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.isSubtypeOf
import org.jetbrains.kotlin.types.Variance

internal class KcpTypeImpl private constructor(
    private val lexicalScope: LexicalScope,
    private val key: KcpTypeKey,
) : CtTypeBase(), LexicalScope by lexicalScope {
    val impl: IrSimpleType
        get() = key.type

    private val rawNameModel: CtTypeNameModel by lazy { impl.rawNameModel(key.jvmKind) }

    override val nameModel: CtTypeNameModel by lazy {
        val arguments = impl.arguments
        if (arguments.isEmpty() || impl.isRawJavaType() || rawNameModel !is ClassNameModel) {
            rawNameModel
        } else {
            ParameterizedNameModel(
                raw = rawNameModel as ClassNameModel,
                typeArguments = arguments.map(::typeArgumentNameModel),
            )
        }
    }

    override val declaration: TypeDeclaration by lazy {
        when {
            key.jvmKind != JvmTypeKind.Declared || impl.isArrayLike() -> NoDeclaration(this)
            impl.classOrNull != null -> KcpTypeDeclarationImpl(this)
            else -> NoDeclaration(this)
        }
    }

    override val typeArguments: List<Type> by lazy {
        if (impl.isRawJavaType()) return@lazy emptyList()
        impl.arguments.map { argument ->
            when (argument) {
                is IrTypeProjection -> kcpType(argument.type, isTypeArgument = true)
                is IrStarProjection -> kcpType(lexicalScope.kcpScope.pluginContext.irBuiltIns.anyType)
            }
        }
    }

    override val isVoid: Boolean
        get() = key.jvmKind == JvmTypeKind.Void

    override fun isAssignableFrom(another: Type): Boolean {
        if (another !is KcpTypeImpl) return false
        val irBuiltIns = lexicalScope.kcpScope.pluginContext.irBuiltIns
        // The Java view of types erases collection mutability (kotlin.collections.MutableX and X
        // both surface as java.util.X and intern to one instance), so assignability must too.
        return another.impl.eraseMutability(irBuiltIns).makeNotNull()
            .isSubtypeOf(impl.eraseMutability(irBuiltIns).makeNotNull(), JavaViewTypeSystemContext(irBuiltIns))
    }

    override fun asBoxed(): Type {
        return if (key.jvmKind == JvmTypeKind.Primitive) {
            KcpTypeImpl(KcpTypeKey(impl, JvmTypeKind.Declared))
        } else {
            this
        }
    }

    private fun typeArgumentNameModel(argument: IrTypeArgument): CtTypeNameModel {
        return when (argument) {
            is IrStarProjection -> WildcardNameModel.Star
            is IrTypeProjection -> {
                val model = (kcpType(argument.type, isTypeArgument = true) as CtNamedType).nameModel
                when (argument.variance) {
                    Variance.INVARIANT -> model
                    Variance.IN_VARIANCE -> WildcardNameModel(lowerBound = model)
                    Variance.OUT_VARIANCE -> WildcardNameModel(upperBound = model)
                }
            }
        }
    }

    companion object Factory : FactoryKey<KcpTypeKey, KcpTypeImpl> {
        override fun LexicalScope.factory() = caching(::KcpTypeImpl)
    }
}

internal class KcpTypeKey(
    val type: IrSimpleType,
    val jvmKind: JvmTypeKind,
) {
    private val canonicalJvmName = type.canonicalJvmName(jvmKind)

    override fun equals(other: Any?): Boolean {
        return this === other || other is KcpTypeKey && canonicalJvmName == other.canonicalJvmName
    }

    override fun hashCode(): Int = canonicalJvmName.hashCode()
}

internal enum class JvmTypeKind {
    Declared,
    Primitive,
    Void,
}
