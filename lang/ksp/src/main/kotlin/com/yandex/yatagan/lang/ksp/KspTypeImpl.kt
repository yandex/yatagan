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

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.NoDeclaration
import com.yandex.yatagan.lang.compiled.CtErrorType
import com.yandex.yatagan.lang.compiled.CtTypeBase
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.InvalidNameModel
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class KspTypeImpl private constructor(
    lexicalScope: LexicalScope,
    resolvedTypeInfo: Pair<JvmTypeInfo, KSType>,
) : CtTypeBase(), LexicalScope by lexicalScope {
    val impl: KSType = resolvedTypeInfo.second
    private val jvmType: JvmTypeInfo = resolvedTypeInfo.first

    override val nameModel: CtTypeNameModel by lazy {
        CtTypeNameModel(type = impl, jvmTypeKind = jvmType)
    }

    override val declaration: TypeDeclaration by lazy(PUBLICATION) {
        if (jvmType is JvmTypeInfo.Declared && impl.declaration is KSClassDeclaration) {
            KspTypeDeclarationImpl(this)
        } else NoDeclaration(this)
    }

    override val typeArguments: List<Type> by lazy {
        when (jvmType) {
            JvmTypeInfo.Declared -> impl.arguments.map { arg ->
                KspTypeImpl(reference = arg.type ?: asReference(Utils.objectType.asStarProjectedType()))
            }
            else -> emptyList()
        }
    }

    override val isVoid: Boolean
        get() = jvmType == JvmTypeInfo.Void || impl == Utils.resolver.builtIns.unitType

    override fun isAssignableFrom(another: Type): Boolean = with(TypeMap) {
        return when (another) {
            // `mapToKotlinType` is required as `isAssignableFrom` doesn't work properly
            // with related Java platform types.
            // https://github.com/google/ksp/issues/890
            is KspTypeImpl -> mapToKotlinType(impl)
                .isAssignableFrom(mapToKotlinType(another.impl))
            else -> false
        }
    }

    override fun asBoxed(): Type {
        return when (jvmType) {
            JvmTypeInfo.Boolean, JvmTypeInfo.Byte, JvmTypeInfo.Char, JvmTypeInfo.Double,
            JvmTypeInfo.Float, JvmTypeInfo.Int, JvmTypeInfo.Long, JvmTypeInfo.Short,
            JvmTypeInfo.Declared,
            -> {
                // Discarding jvmType leads to boxing of primitive types.
                KspTypeImpl(impl = impl)
            }
            JvmTypeInfo.Void, is JvmTypeInfo.Array -> this
        }
    }

    data class ResolveTypeInfo(
        val type: KSType? = null,
        val reference: KSTypeReference? = null,
        val jvmSignatureHint: String? = null,
        val typePosition: TypeMap.Position = TypeMap.Position.Other,
    )

    companion object Factory : FactoryKey<ResolveTypeInfo, Type> {
        private object Caching : FactoryKey<Pair<JvmTypeInfo, KSType>, KspTypeImpl> {
            override fun LexicalScope.factory() = caching(createWithScope = ::KspTypeImpl)
        }

        override fun LexicalScope.factory() = fun LexicalScope.(info: ResolveTypeInfo): Type {
            val (unrefinedType, reference, jvmSignatureHint, typePosition) = info
            val type: KSType? = with(TypeMap) {
                reference?.let {
                    normalizeType(
                        reference = it,
                        position = typePosition,
                    )
                } ?: unrefinedType?.let {
                    normalizeType(type = it)
                }
            }
            return when {
                type == null || type.isError -> {
                    val nameHint = reference?.element?.toString()
                    CtErrorType(
                        nameModel = InvalidNameModel.Unresolved(hint = nameHint ?: jvmSignatureHint),
                    )
                }
                type.declaration is KSTypeParameter -> {
                    CtErrorType(
                        nameModel = InvalidNameModel.TypeVariable(typeVar = type.declaration.simpleName.asString())
                    )
                }
                else -> Caching(JvmTypeInfo(jvmSignature = jvmSignatureHint, type = type) to type)
            }
        }
    }
}

