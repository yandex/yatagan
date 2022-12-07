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

package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.WildcardTypeName
import com.yandex.yatagan.codegen.poetry.MethodSpecBuilder
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.model.ClassBackedModel
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.ArrayNameModel
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.ErrorNameModel
import com.yandex.yatagan.lang.compiled.KeywordTypeNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import com.yandex.yatagan.lang.compiled.WildcardNameModel

internal fun ClassBackedModel.typeName(): TypeName {
    return name.asTypeName()
}

internal fun Type.typeName(): TypeName {
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
    overridee: Method,
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
