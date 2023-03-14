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
import com.google.devtools.ksp.symbol.Variance
import com.yandex.yatagan.lang.compiled.ArrayNameModel
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.InvalidNameModel
import com.yandex.yatagan.lang.compiled.KeywordTypeNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import com.yandex.yatagan.lang.compiled.WildcardNameModel


internal fun CtTypeNameModel(
    type: KSType?,
    jvmTypeKind: JvmTypeInfo? = type?.let(::JvmTypeInfo),
): CtTypeNameModel {
    return nameModelImpl(type = type, jvmTypeKind = jvmTypeKind)
}

private fun nameModelImpl(
    type: KSType?,
    jvmTypeKind: JvmTypeInfo?,
): CtTypeNameModel {
    return when (jvmTypeKind) {
        JvmTypeInfo.Void -> KeywordTypeNameModel.Void
        JvmTypeInfo.Byte -> KeywordTypeNameModel.Byte
        JvmTypeInfo.Char -> KeywordTypeNameModel.Char
        JvmTypeInfo.Double -> KeywordTypeNameModel.Double
        JvmTypeInfo.Float -> KeywordTypeNameModel.Float
        JvmTypeInfo.Int -> KeywordTypeNameModel.Int
        JvmTypeInfo.Long -> KeywordTypeNameModel.Long
        JvmTypeInfo.Short -> KeywordTypeNameModel.Short
        JvmTypeInfo.Boolean -> KeywordTypeNameModel.Boolean
        JvmTypeInfo.Declared -> {
            if (type == null || type.isError) {
                return InvalidNameModel.Unresolved(null)
            }
            val classDeclaration = type.classDeclaration()
                ?: return if (type.declaration is KSTypeParameter) {
                    InvalidNameModel.TypeVariable(typeVar = type.declaration.simpleName.asString())
                } else {
                    InvalidNameModel.Unresolved(hint = type.declaration.simpleName.asString())
                }
            val raw = ClassNameModel(classDeclaration)
            val typeArguments = type.arguments.map { argument ->
                fun argType() = argument.type?.resolve()
                when (argument.variance) {
                    Variance.STAR -> WildcardNameModel.Star
                    Variance.INVARIANT -> CtTypeNameModel(argType())
                    Variance.COVARIANT -> WildcardNameModel(upperBound = CtTypeNameModel(argType()))
                    Variance.CONTRAVARIANT -> WildcardNameModel(lowerBound = CtTypeNameModel(argType()))
                }
            }
            return if (typeArguments.isNotEmpty()) {
                ParameterizedNameModel(raw, typeArguments)
            } else raw
        }
        is JvmTypeInfo.Array -> {
            return ArrayNameModel(
                elementType = CtTypeNameModel(
                    type = type?.arguments?.firstOrNull().let {
                        if (it?.variance == Variance.STAR) {
                            // We don't trust KSP to return the correctly mapped type with STAR projection.
                            Utils.objectType.asStarProjectedType()
                        } else {
                            it?.type?.resolve()
                        }
                    },
                    jvmTypeKind = jvmTypeKind.elementInfo,
                )
            )
        }
        null -> InvalidNameModel.Unresolved(null)
    }
}

private fun ClassNameModel(declaration: KSClassDeclaration): ClassNameModel {
    val packageName = declaration.packageName.asString()
    return ClassNameModel(
        packageName = packageName,
        simpleNames = declaration.qualifiedName?.asString()
            ?.run {
                if (packageName.isNotEmpty()) substring(packageName.length + 1) else this
            }
            ?.split('.') ?: listOf("<unnamed>"),
    )
}
