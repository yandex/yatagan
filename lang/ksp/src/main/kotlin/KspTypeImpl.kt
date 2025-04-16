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
import com.google.devtools.ksp.symbol.Variance
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
    equivalence: KSTypeEquivalence,
    private val jvmType: JvmTypeInfo,
) : CtTypeBase() {
    val impl = equivalence.type

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

    companion object Factory : BiObjectCache<JvmTypeInfo, Factory.KSTypeEquivalence, KspTypeImpl>() {
        /**
         * A polymorphic wrapper over KSType required to correctly cache the KspTypeImpl instances.
         */
        sealed interface KSTypeEquivalence {
            val type: KSType

            /**
             * Use KSType directly - its equals/hashcode suit us.
             */
            @JvmInline value class Ksp1(override val type: KSType) : KSTypeEquivalence

            /**
             * KSP2 uses semantic type equivalence, which ignores "wildcards" - redundant projections.
             * Given `interface Lazy<out T> {}` and `interface Foo`,
             * types `Lazy<Foo>` and `Lazy<out Foo>` are semantically equivalent,
             * but we need to distinguish between them.
             *
             * So we capture type projections and compare them additionally.
             */
            class Ksp2(
                override val type: KSType,
            ) : KSTypeEquivalence {
                private val flatProjections: List<Variance> = buildList { flatten(type) }
                private val hashCode by lazy { type.hashCode() + 31 * flatProjections.hashCode() }
                private fun MutableList<Variance>.flatten(type: KSType?) {
                    type?.arguments?.forEach {
                        add(it.variance)
                        flatten(it.type?.resolve())
                    }
                }

                override fun hashCode(): Int = hashCode
                override fun equals(other: Any?) =
                    other === this || other is Ksp2 && type == other.type && flatProjections == other.flatProjections
            }
        }

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
                else -> {
                    val equivalenceWrapped =
                        if (Utils.isKsp2) KSTypeEquivalence.Ksp2(type) else KSTypeEquivalence.Ksp1(type)
                    createCached(jvmTypeInfo, equivalenceWrapped) {
                        KspTypeImpl(
                            equivalence = equivalenceWrapped,
                            jvmType = jvmTypeInfo,
                        )
                    }
                }
            }
        }
    }
}

