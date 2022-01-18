package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtNoDeclaration
import com.yandex.daggerlite.generator.lang.CtTypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspTypeImpl private constructor(
    val impl: KSType,
    private val jvmType: JvmTypeInfo,
    private val varianceAsWildcard: Boolean,
) : CtTypeLangModel {

    override val nameModel: CtTypeNameModel by lazy(NONE) {
        CtTypeNameModel(type = impl, jvmTypeKind = jvmType)
    }

    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        when (jvmType) {
            JvmTypeInfo.Declared -> KspTypeDeclarationImpl(impl)
            else -> CtNoDeclaration(this)
        }
    }

    override val typeArguments: List<TypeLangModel> by lazy(NONE) {
        when (jvmType) {
            JvmTypeInfo.Declared -> impl.arguments.map { arg ->
                val type = (arg.type?.resolve()?.makeNotNullable() ?: Utils.resolver.builtIns.anyType)
                Factory(type)
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
            is KspTypeImpl -> impl.isAssignableFrom(another.impl)
            else -> false
        }
    }

    override fun toString() = nameModel.toString()

    override fun decay(): TypeLangModel {
        return when (jvmType) {
            JvmTypeInfo.Void -> this
            JvmTypeInfo.Boolean, JvmTypeInfo.Byte, JvmTypeInfo.Char, JvmTypeInfo.Double,
            JvmTypeInfo.Float, JvmTypeInfo.Int, JvmTypeInfo.Long, JvmTypeInfo.Short,
            JvmTypeInfo.Declared,
            -> Factory(impl = impl.makeNotNullable(), varianceAsWildcard = varianceAsWildcard)
            is JvmTypeInfo.Array -> Factory(impl = when (jvmType.elementInfo) {
                JvmTypeInfo.Declared -> {
                    // Make array's element type also not-nullable
                    impl.makeNotNullable().replace(impl.arguments.map { arg ->
                        with(Utils.resolver) {
                            val type = (arg.type?.resolve()?.makeNotNullable() ?: builtIns.anyType)
                            getTypeArgument(createKSTypeReferenceFromKSType(type), Variance.INVARIANT)
                        }
                    })
                }
                else -> impl.makeNotNullable()
            })
        }
    }

    companion object Factory : BiObjectCache<JvmTypeInfo, KSType, KspTypeImpl>() {
        operator fun invoke(
            impl: KSType,
            jvmSignatureHint: String? = null,
            varianceAsWildcard: Boolean = false,
        ): KspTypeImpl {
            val mappedType = mapToJavaPlatformIfNeeded(
                type = impl,
                varianceAsWildcard = varianceAsWildcard,
            )
            val jvmTypeKind = parseJvmSignature(jvmSignatureHint, typeSupplier = { mappedType })
            return createCached(jvmTypeKind, mappedType) {
                KspTypeImpl(
                    impl = mappedType,
                    jvmType = jvmTypeKind,
                    varianceAsWildcard = varianceAsWildcard,
                )
            }
        }

        private fun parseJvmSignature(jvmSignatureHint: CharSequence?, typeSupplier: () -> KSType): JvmTypeInfo {
            return when (jvmSignatureHint?.first()) {
                'B' -> JvmTypeInfo.Byte
                'C' -> JvmTypeInfo.Char
                'D' -> JvmTypeInfo.Double
                'F' -> JvmTypeInfo.Float
                'I' -> JvmTypeInfo.Int
                'J' -> JvmTypeInfo.Long
                'S' -> JvmTypeInfo.Short
                'Z' -> JvmTypeInfo.Boolean
                '[' -> JvmTypeInfo.Array(elementInfo = parseJvmSignature(
                    jvmSignatureHint = jvmSignatureHint.subSequence(startIndex = 1, endIndex = jvmSignatureHint.length),
                    typeSupplier = { typeSupplier().arguments.first().type.resolveOrError() },
                ))
                'L' -> JvmTypeInfo.Declared
                'V' -> JvmTypeInfo.Void
                null -> inferJvmInfoFrom(typeSupplier())
                else -> throw AssertionError("Not reached: unexpected jvm signature: $jvmSignatureHint")
            }
        }
    }
}

internal sealed interface JvmTypeInfo {
    // Primitive
    object Byte : JvmTypeInfo
    object Char : JvmTypeInfo
    object Double : JvmTypeInfo
    object Float : JvmTypeInfo
    object Int : JvmTypeInfo
    object Long : JvmTypeInfo
    object Short : JvmTypeInfo
    object Boolean : JvmTypeInfo

    // Void
    object Void : JvmTypeInfo

    // Declared, relies on mapped KSType
    object Declared : JvmTypeInfo

    // Array
    data class Array(val elementInfo: JvmTypeInfo) : JvmTypeInfo
}