package com.yandex.yatagan.codegen.poetry.java

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeVariableName
import com.squareup.javapoet.WildcardTypeName
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

internal typealias JavaTypeName = com.squareup.javapoet.TypeName
internal typealias JavaClassName = com.squareup.javapoet.ClassName

internal fun buildExpression(block: ExpressionBuilder.() -> Unit): CodeBlock {
    return ExpressionBuilderJavaImpl().apply(block).build()
}

internal fun buildCode(block: CodeBuilder.() -> Unit): CodeBlock {
    return CodeBuilderJavaImpl().apply(block).build()
}

internal fun JavaClassName(className: ClassName): JavaClassName {
    return JavaClassName.get(
        className.packageName,
        className.simpleNames.first(),
        *className.simpleNames.drop(1).toTypedArray(),
    )
}

internal fun JavaTypeName(type: Type): JavaTypeName {
    return JavaTypeName((type as CtNamedType).nameModel)
}

internal fun JavaTypeName(typeName: TypeName): JavaTypeName {
    return when (typeName) {
        TypeName.AnyObject -> JavaClassName.OBJECT
        is TypeName.ArrayList -> ParameterizedTypeName.get(
            JavaClassName.get("java.util", "ArrayList"),
            JavaTypeName(typeName.elementType),
        )
        TypeName.AssertionError -> JavaClassName.get("java.lang", "AssertionError")
        is TypeName.AutoBuilder -> ParameterizedTypeName.get(
            JavaClassName.get("com.yandex.yatagan", "AutoBuilder"),
            JavaTypeName(typeName.t),
        )
        TypeName.Boolean -> JavaClassName.BOOLEAN
        TypeName.Byte -> JavaClassName.BYTE
        is TypeName.Class -> ParameterizedTypeName.get(
            JavaClassName.get("java.lang", "Class"),
            JavaTypeName(typeName.t),
        )
        is ClassName -> JavaClassName(typeName)
        is TypeName.HashMap -> ParameterizedTypeName.get(
            JavaClassName.get("java.util", "HashMap"),
            JavaTypeName(typeName.keyType),
            JavaTypeName(typeName.valueType),
        )
        is TypeName.HashSet -> ParameterizedTypeName.get(
            JavaClassName.get("java.util", "HashSet"),
            JavaTypeName(typeName.elementType),
        )
        is TypeName.Inferred -> JavaTypeName((typeName.type as CtNamedType).nameModel)
        TypeName.Int -> JavaTypeName.INT
        TypeName.Lazy -> JavaClassName.get("com.yandex.yatagan", "Lazy")
        is TypeName.Nullable -> JavaTypeName(typeName.type)
        TypeName.Optional -> JavaClassName.get("com.yandex.yatagan", "Optional")
        TypeName.ThreadAssertions -> JavaClassName.get("com.yandex.yatagan.internal", "ThreadAssertions")
        is TypeName.TypeVariable -> TypeVariableName.get(typeName.name)
    }
}

internal fun JavaClassName(name: ClassNameModel): JavaClassName {
    return when (name.simpleNames.size) {
        0 -> throw IllegalArgumentException()
        1 -> JavaClassName.get(name.packageName, name.simpleNames.first())
        else -> JavaClassName.get(name.packageName,
            name.simpleNames.first(), *name.simpleNames.drop(1).toTypedArray())
    }
}

internal fun JavaTypeName(name: CtTypeNameModel): JavaTypeName {
    return when(name) {
        is ClassNameModel -> JavaClassName(name)
        is ParameterizedNameModel -> ParameterizedTypeName.get(
            JavaClassName(name.raw), *name.typeArguments.map { JavaTypeName(it) }.toTypedArray())
        is WildcardNameModel ->
            name.upperBound?.let { WildcardTypeName.subtypeOf(JavaTypeName(it)) }
                ?: name.lowerBound?.let { WildcardTypeName.supertypeOf(JavaTypeName(it)) }
                ?: WildcardTypeName.subtypeOf(JavaTypeName.OBJECT)
        KeywordTypeNameModel.Boolean -> JavaTypeName.BOOLEAN
        KeywordTypeNameModel.Byte -> JavaTypeName.BYTE
        KeywordTypeNameModel.Int -> JavaTypeName.INT
        KeywordTypeNameModel.Short -> JavaTypeName.SHORT
        KeywordTypeNameModel.Long -> JavaTypeName.LONG
        KeywordTypeNameModel.Float -> JavaTypeName.FLOAT
        KeywordTypeNameModel.Double -> JavaTypeName.DOUBLE
        KeywordTypeNameModel.Char -> JavaTypeName.CHAR
        KeywordTypeNameModel.Void -> JavaTypeName.VOID
        is ArrayNameModel -> ArrayTypeName.of(JavaTypeName(name.elementType))
        is InvalidNameModel -> JavaClassName.get("error", "UnresolvedCla$$")
    }
}

internal fun removeWildcards(typeName: TypeName): JavaTypeName {
    return when(typeName) {
        is TypeName.Inferred -> removeWildcards((typeName.type as CtNamedType).nameModel)
        is TypeName.Nullable -> removeWildcards(typeName.type)
        else -> JavaTypeName(typeName)
    }
}

internal fun removeWildcards(name: CtTypeNameModel): JavaTypeName {
    return when(name) {
        is ArrayNameModel -> removeWildcards(name.elementType)
        is ParameterizedNameModel -> ParameterizedTypeName.get(
            JavaClassName(name.raw), *name.typeArguments.map { removeWildcards(it) }.toTypedArray())
        is WildcardNameModel -> (name.lowerBound ?: name.upperBound)?.let { removeWildcards(it) } ?: JavaClassName.OBJECT
        else -> JavaTypeName(name)
    }
}