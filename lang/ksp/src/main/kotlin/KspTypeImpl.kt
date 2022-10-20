package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import com.yandex.daggerlite.lang.common.NoDeclaration
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class KspTypeImpl private constructor(
    val impl: KSType,
    private val jvmType: JvmTypeInfo,
) : CtTypeLangModel() {

    override val nameModel: CtTypeNameModel by lazy {
        CtTypeNameModel(type = impl, jvmTypeKind = jvmType)
    }

    override val declaration: TypeDeclarationLangModel by lazy(PUBLICATION) {
        if (jvmType is JvmTypeInfo.Declared && impl.declaration is KSClassDeclaration) {
            KspTypeDeclarationImpl(this)
        } else NoDeclaration(this)
    }

    override val typeArguments: List<TypeLangModel> by lazy {
        when (jvmType) {
            JvmTypeInfo.Declared -> impl.arguments.map { arg ->
                Factory(arg.type ?: Utils.objectType.asStarProjectedType().asReference())
            }
            else -> emptyList()
        }
    }

    override val isVoid: Boolean
        get() = jvmType == JvmTypeInfo.Void || impl == Utils.resolver.builtIns.unitType

    override fun isAssignableFrom(another: TypeLangModel): Boolean {
        return when (another) {
            // `mapToKotlinType` is required as `isAssignableFrom` doesn't work properly
            // with related Java platform types.
            // https://github.com/google/ksp/issues/890
            is KspTypeImpl -> TypeMapCache.mapToKotlinType(impl)
                .isAssignableFrom(TypeMapCache.mapToKotlinType(another.impl))
            else -> false
        }
    }

    override fun asBoxed(): TypeLangModel {
        return when (jvmType) {
            JvmTypeInfo.Boolean, JvmTypeInfo.Byte, JvmTypeInfo.Char, JvmTypeInfo.Double,
            JvmTypeInfo.Float, JvmTypeInfo.Int, JvmTypeInfo.Long, JvmTypeInfo.Short,
            JvmTypeInfo.Declared,
            -> {
                // Use direct factory function, as type mapping is already done
                // Discarding jvmType leads to boxing of primitive types.
                obtainType(type = impl, jvmSignatureHint = null /* drop jvm info*/)
            }
            JvmTypeInfo.Void, is JvmTypeInfo.Array -> this
        }
    }

    companion object Factory : BiObjectCache<JvmTypeInfo, KSType, KspTypeImpl>() {
        operator fun invoke(
            reference: KSTypeReference,
            jvmSignatureHint: String? = null,
            typePosition: TypeMapCache.Position = TypeMapCache.Position.Other,
        ): KspTypeImpl = obtainType(
            type = TypeMapCache.normalizeType(
                reference = reference,
                position = typePosition,
            ),
            jvmSignatureHint = jvmSignatureHint,
        )

        operator fun invoke(
            impl: KSType,
            jvmSignatureHint: String? = null,
        ): KspTypeImpl = obtainType(
            type = TypeMapCache.normalizeType(
                type = impl,
            ),
            jvmSignatureHint = jvmSignatureHint,
        )

        private fun obtainType(
            type: KSType,
            jvmSignatureHint: String?,
        ): KspTypeImpl {
            val jvmTypeInfo = JvmTypeInfo(jvmSignature = jvmSignatureHint, type = type)
            return createCached(jvmTypeInfo, type) {
                KspTypeImpl(
                    impl = type,
                    jvmType = jvmTypeInfo,
                )
            }
        }
    }
}

