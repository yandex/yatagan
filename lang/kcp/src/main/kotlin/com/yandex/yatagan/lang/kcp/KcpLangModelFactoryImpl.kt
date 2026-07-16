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

import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.compiled.CtLangModelFactoryBase
import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.Variance

internal class KcpLangModelFactoryImpl(
    private val lexicalScope: LexicalScope,
) : CtLangModelFactoryBase(), LexicalScope by lexicalScope {
    private val scope: KcpLexicalScope
        get() = lexicalScope.kcpScope

    override fun getParameterizedType(
        type: LangModelFactory.ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type {
        val parameterImpl = (parameter as? KcpTypeImpl)?.impl
            ?: return super.getParameterizedType(type, parameter, isCovariant)
        val declaration = when (type) {
            LangModelFactory.ParameterizedType.List -> scope.pluginContext.irBuiltIns.listClass
            LangModelFactory.ParameterizedType.Set -> scope.pluginContext.irBuiltIns.setClass
            LangModelFactory.ParameterizedType.Collection -> scope.pluginContext.irBuiltIns.collectionClass
            LangModelFactory.ParameterizedType.Provider -> findClass("javax.inject.Provider")
        }
        return parameterizedType(declaration, listOf(parameterImpl), isCovariant)
    }

    override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
        val key = (keyType as? KcpTypeImpl)?.impl
            ?: return super.getMapType(keyType, valueType, isCovariant)
        val value = (valueType as? KcpTypeImpl)?.impl
            ?: return super.getMapType(keyType, valueType, isCovariant)
        val map = scope.pluginContext.irBuiltIns.mapClass
        return kcpType(map.typeWithArguments(listOf(
            makeTypeProjection(key, Variance.INVARIANT),
            makeTypeProjection(value, if (isCovariant) Variance.OUT_VARIANCE else Variance.INVARIANT),
        )), position = TypePosition.Synthetic)
    }

    override fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String,
    ): TypeDeclaration? {
        val classId = ClassId(
            FqName(packageName),
            FqName((listOf(simpleName) + simpleNames).joinToString(".")),
            false,
        )
        val declaration = scope.finder.findClass(classId) ?: return null
        if (declaration.owner.kind == ClassKind.ENUM_ENTRY) return null
        return kcpType(declaration.defaultType).declaration
    }

    override val isInRuntimeEnvironment: Boolean
        get() = false

    private fun parameterizedType(
        declaration: IrClassSymbol,
        arguments: List<IrType>,
        isCovariant: Boolean,
    ): Type {
        val variance = if (isCovariant) Variance.OUT_VARIANCE else Variance.INVARIANT
        return kcpType(
            declaration.typeWithArguments(arguments.map { makeTypeProjection(it, variance) }),
            position = TypePosition.Synthetic,
        )
    }

    private fun findClass(qualifiedName: String): IrClassSymbol {
        return checkNotNull(scope.finder.findClass(ClassId.topLevel(FqName(qualifiedName)))) {
            "Unable to resolve $qualifiedName from ${scope.sourceFile.fileEntry.name}"
        }
    }
}
