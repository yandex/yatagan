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

@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.NoDeclaration
import com.yandex.yatagan.lang.compiled.ArrayNameModel
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.CtErrorType
import com.yandex.yatagan.lang.compiled.CtNamedType
import com.yandex.yatagan.lang.compiled.CtTypeBase
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.InvalidNameModel
import com.yandex.yatagan.lang.compiled.KeywordTypeNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import com.yandex.yatagan.lang.compiled.WildcardNameModel
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.IrErrorType
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.impl.buildSimpleType
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.types.impl.toBuilder
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
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
        if (arguments.isEmpty() || rawNameModel !is ClassNameModel) {
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
        val typeSystem = IrTypeSystemContextImpl(lexicalScope.kcpScope.pluginContext.irBuiltIns)
        return another.impl.makeNotNull().isSubtypeOf(impl.makeNotNull(), typeSystem)
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

internal fun LexicalScope.kcpType(type: IrType, isTypeArgument: Boolean = false): Type {
    return when (type) {
        is IrSimpleType -> when (val classifier = type.classifier) {
            is IrClassSymbol -> KcpTypeImpl(KcpTypeKey(
                type = type.normalizeNullabilityRecursively(),
                jvmKind = if (isTypeArgument) JvmTypeKind.Declared else type.jvmTypeKind(),
            ))
            is IrTypeParameterSymbol -> CtErrorType(
                InvalidNameModel.TypeVariable(classifier.owner.name.asString()),
            )
            else -> CtErrorType(InvalidNameModel.Unresolved(type.toString()))
        }
        is IrErrorType, is IrDynamicType -> CtErrorType(InvalidNameModel.Unresolved(type.toString()))
    }
}

internal val LexicalScope.kcpScope: KcpLexicalScope
    get() = ext[KcpScopeKey]

private fun IrSimpleType.rawNameModel(jvmKind: JvmTypeKind): CtTypeNameModel {
    val fqName = classOrNull?.owner?.fqNameWhenAvailable?.asString()
        ?: return InvalidNameModel.Unresolved(toString())
    when (jvmKind) {
        JvmTypeKind.Primitive -> return checkNotNull(PrimitiveKeywords[fqName])
        JvmTypeKind.Void -> return KeywordTypeNameModel.Void
        JvmTypeKind.Declared -> Unit
    }
    PrimitiveArrays[fqName]?.let { return ArrayNameModel(it) }
    if (fqName == "kotlin.Array") {
        val element = arguments.firstOrNull()
        return ArrayNameModel(when (element) {
            is IrTypeProjection -> (element.type as? IrSimpleType)?.rawArrayElementNameModel()
                ?: InvalidNameModel.Unresolved(element.type.toString())
            is IrStarProjection, null -> WildcardNameModel.Star
        })
    }
    JavaClassNames[fqName]?.let { return it }
    fqName.jvmFunctionNameOrNull()?.let { return it }

    val classId = classOrNull?.owner?.classId
    return if (classId != null) {
        ClassNameModel(
            packageName = classId.packageFqName.asString(),
            simpleNames = classId.relativeClassName.pathSegments().map { it.asString() },
        )
    } else {
        ClassNameModel(packageName = "", simpleNames = listOf(classOrNull!!.owner.name.asString()))
    }
}

private fun String.jvmFunctionNameOrNull(): ClassNameModel? {
    val simpleName = removePrefix("kotlin.")
    val arity = simpleName.removePrefix("Function")
    if (simpleName == this || arity == simpleName || arity.toIntOrNull() == null) return null
    return ClassNameModel("kotlin.jvm.functions", listOf(simpleName))
}

private fun IrSimpleType.rawArrayElementNameModel(): CtTypeNameModel {
    val fqName = classOrNull?.owner?.fqNameWhenAvailable?.asString()
    JavaClassNames[fqName]?.let { return it }
    return rawNameModel(JvmTypeKind.Declared)
}

private fun IrSimpleType.isArrayLike(): Boolean {
    val fqName = classOrNull?.owner?.fqNameWhenAvailable?.asString()
    return fqName == "kotlin.Array" || fqName in PrimitiveArrays
}

private fun IrSimpleType.jvmTypeKind(): JvmTypeKind {
    val fqName = classOrNull?.owner?.fqNameWhenAvailable?.asString()
    return when {
        isMarkedNullable() -> JvmTypeKind.Declared
        fqName == "kotlin.Unit" -> JvmTypeKind.Void
        fqName in PrimitiveKeywords -> JvmTypeKind.Primitive
        else -> JvmTypeKind.Declared
    }
}

private fun IrSimpleType.normalizeNullabilityRecursively(): IrSimpleType {
    val normalizedArguments = arguments.map { argument ->
        when (argument) {
            is IrStarProjection -> argument
            is IrTypeProjection -> makeTypeProjection(
                argument.type.normalizeNullabilityRecursively(),
                argument.variance,
            )
        }
    }
    return (makeNotNull() as IrSimpleType).toBuilder().apply {
        nullability = SimpleTypeNullability.DEFINITELY_NOT_NULL
        arguments = normalizedArguments
    }.buildSimpleType()
}

private fun IrType.normalizeNullabilityRecursively(): IrType {
    return when (this) {
        is IrSimpleType -> normalizeNullabilityRecursively()
        is IrErrorType, is IrDynamicType -> this
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

private fun IrSimpleType.canonicalJvmName(jvmKind: JvmTypeKind): String {
    val raw = rawNameModel(jvmKind)
    if (arguments.isEmpty() || raw !is ClassNameModel) return raw.toString()
    return arguments.joinToString(prefix = "$raw<", postfix = ">") { argument ->
        when (argument) {
            is IrStarProjection -> "?"
            is IrTypeProjection -> {
                val typeName = argument.type.canonicalTypeArgumentName()
                when (argument.variance) {
                    Variance.INVARIANT -> typeName
                    Variance.IN_VARIANCE -> "? super $typeName"
                    Variance.OUT_VARIANCE -> "? extends $typeName"
                }
            }
        }
    }
}

private fun IrType.canonicalTypeArgumentName(): String {
    return when (this) {
        is IrSimpleType -> when (val classifier = classifier) {
            is IrClassSymbol -> normalizeNullabilityRecursively().canonicalJvmName(JvmTypeKind.Declared)
            is IrTypeParameterSymbol -> "<unresolved-type-var: ${classifier.owner.name}>"
            else -> "<unresolved: $this>"
        }
        is IrErrorType, is IrDynamicType -> "<unresolved: $this>"
    }
}

private val PrimitiveKeywords = mapOf(
    "kotlin.Boolean" to KeywordTypeNameModel.Boolean,
    "kotlin.Byte" to KeywordTypeNameModel.Byte,
    "kotlin.Short" to KeywordTypeNameModel.Short,
    "kotlin.Int" to KeywordTypeNameModel.Int,
    "kotlin.Long" to KeywordTypeNameModel.Long,
    "kotlin.Char" to KeywordTypeNameModel.Char,
    "kotlin.Float" to KeywordTypeNameModel.Float,
    "kotlin.Double" to KeywordTypeNameModel.Double,
    "kotlin.Unit" to KeywordTypeNameModel.Void,
)

private val PrimitiveArrays = mapOf(
    "kotlin.BooleanArray" to KeywordTypeNameModel.Boolean,
    "kotlin.ByteArray" to KeywordTypeNameModel.Byte,
    "kotlin.ShortArray" to KeywordTypeNameModel.Short,
    "kotlin.IntArray" to KeywordTypeNameModel.Int,
    "kotlin.LongArray" to KeywordTypeNameModel.Long,
    "kotlin.CharArray" to KeywordTypeNameModel.Char,
    "kotlin.FloatArray" to KeywordTypeNameModel.Float,
    "kotlin.DoubleArray" to KeywordTypeNameModel.Double,
)

private val JavaClassNames = mapOf(
    "kotlin.Any" to ClassNameModel("java.lang", listOf("Object")),
    "kotlin.String" to ClassNameModel("java.lang", listOf("String")),
    "kotlin.CharSequence" to ClassNameModel("java.lang", listOf("CharSequence")),
    "kotlin.Number" to ClassNameModel("java.lang", listOf("Number")),
    "kotlin.Throwable" to ClassNameModel("java.lang", listOf("Throwable")),
    "kotlin.Comparable" to ClassNameModel("java.lang", listOf("Comparable")),
    "kotlin.Enum" to ClassNameModel("java.lang", listOf("Enum")),
    "kotlin.Annotation" to ClassNameModel("java.lang.annotation", listOf("Annotation")),
    "kotlin.reflect.KClass" to ClassNameModel("java.lang", listOf("Class")),
    "kotlin.collections.Iterable" to ClassNameModel("java.lang", listOf("Iterable")),
    "kotlin.collections.Collection" to ClassNameModel("java.util", listOf("Collection")),
    "kotlin.collections.MutableCollection" to ClassNameModel("java.util", listOf("Collection")),
    "kotlin.collections.List" to ClassNameModel("java.util", listOf("List")),
    "kotlin.collections.MutableList" to ClassNameModel("java.util", listOf("List")),
    "kotlin.collections.Set" to ClassNameModel("java.util", listOf("Set")),
    "kotlin.collections.MutableSet" to ClassNameModel("java.util", listOf("Set")),
    "kotlin.collections.Map" to ClassNameModel("java.util", listOf("Map")),
    "kotlin.collections.MutableMap" to ClassNameModel("java.util", listOf("Map")),
    "kotlin.collections.Map.Entry" to ClassNameModel("java.util", listOf("Map", "Entry")),
    "kotlin.collections.MutableMap.MutableEntry" to ClassNameModel("java.util", listOf("Map", "Entry")),
    "kotlin.Boolean" to ClassNameModel("java.lang", listOf("Boolean")),
    "kotlin.Byte" to ClassNameModel("java.lang", listOf("Byte")),
    "kotlin.Short" to ClassNameModel("java.lang", listOf("Short")),
    "kotlin.Int" to ClassNameModel("java.lang", listOf("Integer")),
    "kotlin.Long" to ClassNameModel("java.lang", listOf("Long")),
    "kotlin.Char" to ClassNameModel("java.lang", listOf("Character")),
    "kotlin.Float" to ClassNameModel("java.lang", listOf("Float")),
    "kotlin.Double" to ClassNameModel("java.lang", listOf("Double")),
    "kotlin.Unit" to ClassNameModel("kotlin", listOf("Unit")),
)
