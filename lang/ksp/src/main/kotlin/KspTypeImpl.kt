package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.NoDeclaration
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspTypeImpl private constructor(
    val impl: KSType,
    private val jvmType: JvmTypeInfo,
) : CtTypeLangModel {

    override val nameModel: CtTypeNameModel by lazy(NONE) {
        CtTypeNameModel(type = impl, jvmTypeKind = jvmType)
    }

    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        when (jvmType) {
            JvmTypeInfo.Declared -> KspTypeDeclarationImpl(this)
            else -> NoDeclaration(this)
        }
    }

    override val typeArguments: List<TypeLangModel> by lazy(NONE) {
        when (jvmType) {
            JvmTypeInfo.Declared -> impl.arguments.map { arg ->
                Factory(arg.type?.resolve() ?: Utils.resolver.builtIns.anyType)
            }
            else -> emptyList()
        }
    }

    override val isBoolean: Boolean
        get() = jvmType == JvmTypeInfo.Boolean || impl.declaration == Utils.javaLangBoolean

    override val isVoid: Boolean
        get() = jvmType == JvmTypeInfo.Void || impl == Utils.resolver.builtIns.unitType

    override fun isAssignableFrom(another: TypeLangModel): Boolean {
        return when (another) {
            is KspTypeImpl -> impl.isAssignableFrom(when (val type = another.impl) {
                is TypeMapCache.RawType -> type.underlying
                else -> type
            })
            else -> false
        }
    }

    override fun toString() = nameModel.toString()

    override fun decay(): TypeLangModel {
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

