package com.yandex.yatagan.codegen.poetry.kotlin

import com.squareup.kotlinpoet.ANNOTATION
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BOOLEAN_ARRAY
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.CHAR_ARRAY
import com.squareup.kotlinpoet.CHAR_SEQUENCE
import com.squareup.kotlinpoet.COLLECTION
import com.squareup.kotlinpoet.COMPARABLE
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.DOUBLE_ARRAY
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FLOAT_ARRAY
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.ITERABLE
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MAP_ENTRY
import com.squareup.kotlinpoet.NUMBER
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.SHORT_ARRAY
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.THROWABLE
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.ArrayNameModel
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.CtNamedType
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.InvalidNameModel
import com.yandex.yatagan.lang.compiled.KeywordTypeNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import com.yandex.yatagan.lang.compiled.WildcardNameModel

internal typealias KotlinTypeName = com.squareup.kotlinpoet.TypeName
internal typealias KotlinClassName = com.squareup.kotlinpoet.ClassName

internal fun buildExpression(block: ExpressionBuilder.() -> Unit): CodeBlock {
    return ExpressionBuilderKotlinImpl().apply(block).build()
}

internal fun buildCode(block: CodeBuilder.() -> Unit): CodeBlock {
    return CodeBuilderKotlinImpl().apply(block).build()
}

internal fun defaultInitializerFor(type: KotlinTypeName): CodeBlock? {
    if (type.isNullable) return CodeBlock.of("null")
    return when (type) {
        BOOLEAN -> CodeBlock.of("false")
        BYTE, SHORT, INT, LONG -> CodeBlock.of("0")
        FLOAT -> CodeBlock.of("0.0f")
        DOUBLE -> CodeBlock.of("0.0")
        else -> null
    }
}

internal fun KotlinClassName(name: ClassNameModel) : KotlinClassName {
    return KotlinClassName(ClassName(
        packageName = name.packageName,
        simpleNames = name.simpleNames,
    ))
}

private val Java2KotlinMap = mapOf(
    KotlinClassName("java.lang", "Object") to ANY,
    KotlinClassName("java.lang", "Boolean") to BOOLEAN,
    KotlinClassName("java.lang", "Byte") to BYTE,
    KotlinClassName("java.lang", "Short") to SHORT,
    KotlinClassName("java.lang", "Integer") to INT,
    KotlinClassName("java.lang", "Long") to LONG,
    KotlinClassName("java.lang", "Character") to CHAR,
    KotlinClassName("java.lang", "Float") to FLOAT,
    KotlinClassName("java.lang", "Double") to DOUBLE,
    KotlinClassName("java.lang", "String") to STRING,
    KotlinClassName("java.lang", "CharSequence") to CHAR_SEQUENCE,
    KotlinClassName("java.lang", "Comparable") to COMPARABLE,
    KotlinClassName("java.lang", "Throwable") to THROWABLE,
    KotlinClassName("java.lang", "Number") to NUMBER,
    KotlinClassName("java.lang", "Iterable") to ITERABLE,
    KotlinClassName("java.util", "Collection") to COLLECTION,
    KotlinClassName("java.util", "List") to LIST,
    KotlinClassName("java.util", "Set") to SET,
    KotlinClassName("java.util", "Map") to MAP,
    KotlinClassName("java.util", "Map", "Entry") to MAP_ENTRY,
    KotlinClassName("java.lang.annotation", "Annotation") to ANNOTATION,
)

internal fun KotlinClassName(name: ClassName): KotlinClassName {
    val kotlinClassName = KotlinClassName(
        packageName = name.packageName,
        simpleNames = name.simpleNames,
    )
    if (kotlinClassName.packageName.startsWith("java.")) {
        return Java2KotlinMap[kotlinClassName] ?: kotlinClassName
    }
    return kotlinClassName
}

internal fun KotlinTypeName(type: Type): KotlinTypeName {
    return KotlinTypeName((type as CtNamedType).nameModel)
}

internal fun TypeVariableName(typeVariable: TypeName.TypeVariable): TypeVariableName {
    return if (typeVariable.extendsAnyWildcard)
        TypeVariableName(typeVariable.name, ANY)
    else
        TypeVariableName(typeVariable.name)
}

internal fun KotlinTypeName(name: TypeName): KotlinTypeName {
    return when(name) {
        TypeName.AnyObject -> ANY
        is TypeName.ArrayList ->
            KotlinClassName("kotlin.collections", "ArrayList").parameterizedBy(KotlinTypeName(name.elementType))
        TypeName.AssertionError -> KotlinClassName("kotlin", "AssertionError")
        is TypeName.AutoBuilder ->
            KotlinClassName("com.yandex.yatagan", "AutoBuilder").parameterizedBy(KotlinTypeName(name.t))
        TypeName.Boolean -> BOOLEAN
        TypeName.Byte -> BYTE
        is TypeName.Class ->
            KotlinClassName("java.lang", "Class").parameterizedBy(KotlinTypeName(name.t))
        is ClassName -> KotlinClassName(name)
        is TypeName.HashMap -> KotlinClassName("kotlin.collections", "HashMap").parameterizedBy(
            KotlinTypeName(name.keyType),
            KotlinTypeName(name.valueType),
        )
        is TypeName.HashSet ->
            KotlinClassName("kotlin.collections", "HashSet").parameterizedBy(KotlinTypeName(name.elementType))
        is TypeName.Inferred -> KotlinTypeName(name.type)
        TypeName.Int -> INT
        is TypeName.Lazy ->
            KotlinClassName("com.yandex.yatagan", "Lazy").parameterizedBy(KotlinTypeName(name.t))
        is TypeName.Provider ->
            KotlinClassName("javax.inject", "Provider").parameterizedBy(KotlinTypeName(name.t))
        is TypeName.Nullable -> KotlinTypeName(name.type).copy(nullable = true)
        is TypeName.Optional ->
            KotlinClassName("com.yandex.yatagan", "Optional").parameterizedBy(KotlinTypeName(name.t))
        TypeName.OptionalRaw -> KotlinClassName("com.yandex.yatagan", "Optional")
        TypeName.ThreadAssertions -> KotlinClassName("com.yandex.yatagan.internal", "ThreadAssertions")
        is TypeName.TypeVariable -> TypeVariableName(name)
    }
}

internal fun KotlinTypeName(name: CtTypeNameModel): KotlinTypeName {
    return when (name) {
        is ArrayNameModel -> when(name.elementType) {
            KeywordTypeNameModel.Void -> throw AssertionError()
            KeywordTypeNameModel.Boolean -> BOOLEAN_ARRAY
            KeywordTypeNameModel.Byte -> BYTE_ARRAY
            KeywordTypeNameModel.Int -> INT_ARRAY
            KeywordTypeNameModel.Short -> SHORT_ARRAY
            KeywordTypeNameModel.Long -> LONG_ARRAY
            KeywordTypeNameModel.Float -> FLOAT_ARRAY
            KeywordTypeNameModel.Double -> DOUBLE_ARRAY
            KeywordTypeNameModel.Char -> CHAR_ARRAY
            else -> ARRAY.parameterizedBy(KotlinTypeName(name.elementType))
        }
        is ClassNameModel -> KotlinClassName(name)
        is InvalidNameModel -> KotlinClassName("error", "UnresolvedCla$$")
        KeywordTypeNameModel.Void -> UNIT
        KeywordTypeNameModel.Boolean -> BOOLEAN
        KeywordTypeNameModel.Byte -> BYTE
        KeywordTypeNameModel.Int -> INT
        KeywordTypeNameModel.Short -> SHORT
        KeywordTypeNameModel.Long -> LONG
        KeywordTypeNameModel.Float -> FLOAT
        KeywordTypeNameModel.Double -> DOUBLE
        KeywordTypeNameModel.Char -> CHAR
        is ParameterizedNameModel -> KotlinClassName(name.raw).parameterizedBy(
            name.typeArguments.map { KotlinTypeName(it) },
        )
        is WildcardNameModel -> name.lowerBound?.let {
            WildcardTypeName.consumerOf(KotlinTypeName(it))
        } ?: name.upperBound?.let {
            WildcardTypeName.producerOf(KotlinTypeName(it))
        } ?: STAR
    }
}

fun removeProjections(name: KotlinTypeName): KotlinTypeName {
    return when(name) {
        is ParameterizedTypeName -> name.copy(
            typeArguments = name.typeArguments.map { removeProjections(it) },
        )
        is TypeVariableName -> name.copy(
            bounds = emptyList(),
        )
        is WildcardTypeName -> name.inTypes.firstOrNull() ?: name.outTypes.firstOrNull() ?: ANY
        else -> name
    }
}