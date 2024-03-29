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
import com.yandex.yatagan.base.BiObjectCache
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.NoDeclaration
import com.yandex.yatagan.lang.compiled.CtErrorType
import com.yandex.yatagan.lang.compiled.CtTypeBase
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.InvalidNameModel
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class KspTypeImpl private constructor(
    val impl: KSType,
    private val jvmType: JvmTypeInfo,
) : CtTypeBase() {

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
                Factory(arg.type ?: Utils.objectType.asStarProjectedType().asReference())
            }
            else -> emptyList()
        }
    }

    override val isVoid: Boolean
        get() = jvmType == JvmTypeInfo.Void || impl == Utils.resolver.builtIns.unitType

    override fun isAssignableFrom(another: Type): Boolean {
        return when (another) {
            // `mapToKotlinType` is required as `isAssignableFrom` doesn't work properly
            // with related Java platform types.
            // https://github.com/google/ksp/issues/890
            is KspTypeImpl -> TypeMapCache.mapToKotlinType(impl)
                .isAssignableFrom(TypeMapCache.mapToKotlinType(another.impl))
            else -> false
        }
    }

    override fun asBoxed(): Type {
        return when (jvmType) {
            JvmTypeInfo.Boolean, JvmTypeInfo.Byte, JvmTypeInfo.Char, JvmTypeInfo.Double,
            JvmTypeInfo.Float, JvmTypeInfo.Int, JvmTypeInfo.Long, JvmTypeInfo.Short,
            JvmTypeInfo.Declared,
            -> {
                // Use direct factory function, as type mapping is already done
                // Discarding jvmType leads to boxing of primitive types.
                obtainType(type = impl, jvmSignatureHint = null /* drop jvm info*/, nameHint = null)
            }
            JvmTypeInfo.Void, is JvmTypeInfo.Array -> this
        }
    }

    companion object Factory : BiObjectCache<JvmTypeInfo, KSType, KspTypeImpl>() {
        operator fun invoke(
            reference: KSTypeReference?,
            jvmSignatureHint: String? = null,
            typePosition: TypeMapCache.Position = TypeMapCache.Position.Other,
        ): Type = obtainType(
            type = reference?.let {
                TypeMapCache.normalizeType(
                    reference = it,
                    position = typePosition,
                )
            },
            jvmSignatureHint = jvmSignatureHint,
            nameHint = reference?.element?.toString(),
        )

        operator fun invoke(
            impl: KSType?,
            jvmSignatureHint: String? = null,
        ): Type = obtainType(
            type = impl?.let {
                TypeMapCache.normalizeType(
                    type = it,
                )
            },
            jvmSignatureHint = jvmSignatureHint,
            nameHint = null,
        )

        private fun obtainType(
            type: KSType?,
            jvmSignatureHint: String?,
            nameHint: String?,
        ): Type {
            val jvmTypeInfo = JvmTypeInfo(jvmSignature = jvmSignatureHint, type = type)
            return when {
                type == null || type.isError -> {
                    CtErrorType(
                        nameModel = InvalidNameModel.Unresolved(hint = nameHint ?: jvmSignatureHint),
                    )
                }
                type.declaration is KSTypeParameter -> {
                    CtErrorType(
                        nameModel = InvalidNameModel.TypeVariable(typeVar = type.declaration.simpleName.asString())
                    )
                }
                else -> createCached(jvmTypeInfo, type) {
                    KspTypeImpl(
                        impl = type,
                        jvmType = jvmTypeInfo,
                    )
                }
            }
        }
    }
}

