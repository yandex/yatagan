package com.yandex.daggerlite.codegen.impl

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.WildcardTypeName
import com.yandex.daggerlite.codegen.poetry.MethodSpecBuilder
import com.yandex.daggerlite.codegen.poetry.TypeSpecBuilder
import com.yandex.daggerlite.core.model.ClassBackedModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.TypeLangModel
import com.yandex.daggerlite.lang.compiled.ArrayNameModel
import com.yandex.daggerlite.lang.compiled.ClassNameModel
import com.yandex.daggerlite.lang.compiled.CtTypeNameModel
import com.yandex.daggerlite.lang.compiled.ErrorNameModel
import com.yandex.daggerlite.lang.compiled.KeywordTypeNameModel
import com.yandex.daggerlite.lang.compiled.ParameterizedNameModel
import com.yandex.daggerlite.lang.compiled.WildcardNameModel

internal fun ClassBackedModel.typeName(): TypeName {
    return name.asTypeName()
}

internal fun TypeLangModel.typeName(): TypeName {
    return name.asTypeName()
}

internal fun TypeName.asRawType(): TypeName = when(this) {
    is ParameterizedTypeName -> this.rawType
    else -> this
}

private fun ClassNameModel.asTypeName(): ClassName {
    return when (simpleNames.size) {
        0 -> throw IllegalArgumentException()
        1 -> ClassName.get(packageName, simpleNames.first())
        else -> ClassName.get(packageName, simpleNames.first(), *simpleNames.drop(1).toTypedArray())
    }
}

internal fun CtTypeNameModel.asTypeName(): TypeName {
    return when (this) {
        is ClassNameModel -> asTypeName()
        is ParameterizedNameModel -> ParameterizedTypeName.get(
            raw.asTypeName(), *typeArguments.map { it.asTypeName() }.toTypedArray())
        is WildcardNameModel ->
            upperBound?.let { WildcardTypeName.subtypeOf(it.asTypeName()) }
                ?: lowerBound?.let { WildcardTypeName.supertypeOf(it.asTypeName()) }
                ?: WildcardTypeName.subtypeOf(TypeName.OBJECT)
        KeywordTypeNameModel.Boolean -> TypeName.BOOLEAN
        KeywordTypeNameModel.Byte -> TypeName.BYTE
        KeywordTypeNameModel.Int -> TypeName.INT
        KeywordTypeNameModel.Short -> TypeName.SHORT
        KeywordTypeNameModel.Long -> TypeName.LONG
        KeywordTypeNameModel.Float -> TypeName.FLOAT
        KeywordTypeNameModel.Double -> TypeName.DOUBLE
        KeywordTypeNameModel.Char -> TypeName.CHAR
        KeywordTypeNameModel.Void -> TypeName.VOID
        is ArrayNameModel -> ArrayTypeName.of(elementType.asTypeName())
        is ErrorNameModel -> ClassName.get("", toString())
    }
}

internal inline fun TypeSpecBuilder.overrideMethod(
    overridee: FunctionLangModel,
    block: MethodSpecBuilder.() -> Unit,
) {
    method(name = overridee.name) {
        annotation<Override>()
        returnType(overridee.returnType.typeName())
        overridee.parameters.forEach { parameterModel ->
            parameter(parameterModel.type.typeName(), name = parameterModel.name)
        }
        block()
    }
}
