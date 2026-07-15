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

@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.codegen.ir

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class RuntimeSymbols(
    private val pluginContext: IrPluginContext,
    private val finder: DeclarationFinder,
) {
    private fun classSymbol(fqName: String): IrClassSymbol =
        finder.findClass(ClassId.topLevel(FqName(fqName)))
            ?: unsupported("class $fqName is not on the compilation classpath")

    private fun topLevelFunction(packageFqName: String, name: String): IrSimpleFunctionSymbol =
        finder.findFunctions(CallableId(FqName(packageFqName), Name.identifier(name)))
            .singleOrNull()
            ?: unsupported("function $packageFqName.$name is not on the compilation classpath")

    val lazyClass: IrClassSymbol by lazy { classSymbol("com.yandex.yatagan.Lazy") }
    val providerGet: IrSimpleFunctionSymbol by lazy {
        classSymbol("javax.inject.Provider").owner.functions
            .single { it.name.asString() == "get" }.symbol
    }
    val optionalClass: IrClassSymbol by lazy { classSymbol("com.yandex.yatagan.Optional") }
    val optionalCompanion: IrClass by lazy {
        optionalClass.owner.declarations.filterIsInstance<IrClass>().single { it.isCompanion }
    }
    val optionalOf: IrSimpleFunction by lazy {
        optionalCompanion.functions.single { it.name.asString() == "of" }
    }
    val optionalEmpty: IrSimpleFunction by lazy {
        optionalCompanion.functions.single { it.name.asString() == "empty" }
    }
    val checkInputNotNull: IrSimpleFunctionSymbol by lazy {
        topLevelFunction("com.yandex.yatagan.internal", "checkInputNotNull")
    }
    val checkProvisionNotNull: IrSimpleFunctionSymbol by lazy {
        topLevelFunction("com.yandex.yatagan.internal", "checkProvisionNotNull")
    }
    val yataganGeneratedConstructor: IrConstructorSymbol? by lazy {
        finder.findClass(
            ClassId(FqName("com.yandex.yatagan.internal"), Name.identifier("YataganGenerated")),
        )?.owner?.constructors?.singleOrNull()?.symbol
    }
    val autoBuilderClass: IrClassSymbol by lazy { classSymbol("com.yandex.yatagan.AutoBuilder") }
    val javaLangClass: IrClassSymbol by lazy { classSymbol("java.lang.Class") }
    val reportUnexpectedAutoBuilderInput: IrSimpleFunctionSymbol by lazy {
        topLevelFunction("com.yandex.yatagan.internal", "reportUnexpectedAutoBuilderInput")
    }
    val reportMissingAutoBuilderInput: IrSimpleFunctionSymbol by lazy {
        topLevelFunction("com.yandex.yatagan.internal", "reportMissingAutoBuilderInput")
    }
    val listOfVararg: IrSimpleFunctionSymbol by lazy {
        finder.findFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("listOf")))
            .single { it.owner.regularParameters().singleOrNull()?.varargElementType != null }
    }
    val arrayListClass: IrClassSymbol by lazy { classSymbol("java.util.ArrayList") }
    val hashSetClass: IrClassSymbol by lazy { classSymbol("java.util.HashSet") }
    val hashMapClass: IrClassSymbol by lazy { classSymbol("java.util.HashMap") }
    val mutableMapPut: IrSimpleFunctionSymbol by lazy {
        pluginContext.irBuiltIns.mutableMapClass.owner.functions
            .single { it.name.asString() == "put" && it.regularParameters().size == 2 }.symbol
    }
    val mutableMapPutAll: IrSimpleFunctionSymbol by lazy {
        pluginContext.irBuiltIns.mutableMapClass.owner.functions
            .single { it.name.asString() == "putAll" && it.regularParameters().size == 1 }.symbol
    }
    val getJavaClass: IrSimpleFunctionSymbol by lazy {
        // `KClass<T>.java` extension property from kotlin.jvm.JvmClassMapping; its getter is
        // the backend intrinsic that lowers to a plain class constant.
        finder.findProperties(CallableId(FqName("kotlin.jvm"), Name.identifier("java")))
            .mapNotNull { property ->
                property.owner.getter?.takeIf { getter ->
                    getter.parameters.singleOrNull { it.kind == IrParameterKind.ExtensionReceiver }
                        ?.type?.classOrNull == pluginContext.irBuiltIns.kClassClass
                }
            }
            .singleOrNull()?.symbol
            ?: unsupported("kotlin.jvm.java extension property is not on the compilation classpath")
    }
    val mutableCollectionAdd: IrSimpleFunctionSymbol by lazy {
        pluginContext.irBuiltIns.mutableCollectionClass.owner.functions
            .single { it.name.asString() == "add" && it.regularParameters().size == 1 }.symbol
    }
    val mutableCollectionAddAll: IrSimpleFunctionSymbol by lazy {
        pluginContext.irBuiltIns.mutableCollectionClass.owner.functions
            .single { it.name.asString() == "addAll" && it.regularParameters().size == 1 }.symbol
    }
    val volatileAnnotationConstructor: IrConstructorSymbol by lazy {
        classSymbol("kotlin.jvm.Volatile").owner.constructors.single().symbol
    }
    val synchronizedAnnotationConstructor: IrConstructorSymbol by lazy {
        classSymbol("kotlin.jvm.Synchronized").owner.constructors.single().symbol
    }
    val assertionErrorConstructor: IrConstructorSymbol by lazy {
        classSymbol("kotlin.AssertionError").owner.constructors
            .single { it.regularParameters().isEmpty() }.symbol
    }
}
