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

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.lang.Type
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name

internal val YataganIrOrigin = IrDeclarationOriginImpl("YATAGAN_GRAPH_IR")

internal fun IrFunction.regularParameters(): List<IrValueParameter> =
    parameters.filter { it.kind == IrParameterKind.Regular }

internal fun regularArgumentOffset(function: IrFunction): Int =
    function.parameters.indexOfFirst { it.kind == IrParameterKind.Regular }.also { check(it >= 0) }

internal fun IrType.substituteTypeParameters(substitution: Map<IrTypeParameterSymbol, IrType>): IrType {
    if (substitution.isEmpty()) return this
    val simple = this as? IrSimpleType ?: return this
    (simple.classifier as? IrTypeParameterSymbol)?.let { typeParameter ->
        return substitution[typeParameter] ?: this
    }
    val classSymbol = simple.classOrNull ?: return this
    if (simple.arguments.isEmpty()) return this
    val newArguments = simple.arguments.map { argument ->
        (argument as? IrTypeProjection)?.type?.substituteTypeParameters(substitution)
            ?: return this // star projections and the like - leave the type as is
    }
    return classSymbol.typeWith(newArguments)
}

/**
 * Creates an override of [overridden] in this class, handling both plain methods and
 * property accessors (which require an [org.jetbrains.kotlin.ir.declarations.IrProperty] wrapper).
 */
internal fun IrClass.addOverride(
    overridden: IrSimpleFunctionSymbol,
    substitution: Map<IrTypeParameterSymbol, IrType> = emptyMap(),
): IrSimpleFunction {
    val overriddenFn = overridden.owner
    val correspondingProperty = overriddenFn.correspondingPropertySymbol
    val function = if (correspondingProperty != null) {
        val property = addProperty {
            name = correspondingProperty.owner.name
            origin = YataganIrOrigin
        }.apply {
            overriddenSymbols = listOf(correspondingProperty)
        }
        property.addGetter {
            returnType = overriddenFn.returnType.substituteTypeParameters(substitution)
            modality = Modality.FINAL
            visibility = DescriptorVisibilities.PUBLIC
            origin = YataganIrOrigin
        }
    } else {
        addFunction(
            name = overriddenFn.name.asString(),
            returnType = overriddenFn.returnType.substituteTypeParameters(substitution),
            modality = Modality.FINAL,
            visibility = DescriptorVisibilities.PUBLIC,
            origin = YataganIrOrigin,
        )
    }
    return function.apply {
        if (dispatchReceiverParameter == null) {
            parameters += this@addOverride.thisReceiver!!.copyTo(
                this, type = this@addOverride.defaultType, kind = IrParameterKind.DispatchReceiver,
            )
        }
        overriddenSymbols = listOf(overridden)
        overriddenFn.regularParameters().forEach { parameter ->
            addValueParameter(parameter.name.asString(), parameter.type.substituteTypeParameters(substitution))
        }
    }
}

internal fun rootImplementationName(component: IrClass): Name {
    val nesting = generateSequence(component) { declaration -> declaration.parent as? IrClass }
        .toList()
        .asReversed()
    return Name.identifier("Yatagan" + nesting.joinToString("_") { it.name.asString() })
}

internal fun childImplementationName(parentImpl: IrClass, component: IrClass): Name {
    val base = component.name.asString() + "Impl"
    var candidate = base
    var counter = 0
    while (parentImpl.declarations.filterIsInstance<IrClass>().any { it.name.asString() == candidate }) {
        candidate = base + counter++
    }
    return Name.identifier(candidate)
}

internal fun sanitizeName(name: String): String =
    name.filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { "arg" }

internal fun ComponentTreeEmitter.irJavaClassLiteral(
    builder: IrBuilderWithScope,
    classSymbol: IrClassSymbol,
): IrExpression {
    val classType = classSymbol.defaultType
    val kClassReference = IrClassReferenceImpl(
        builder.startOffset, builder.endOffset,
        irBuiltIns.kClassClass.typeWith(classType),
        classSymbol,
        classType,
    )
    return builder.irCall(symbols.getJavaClass).apply {
        typeArguments[0] = classType
        arguments[symbols.getJavaClass.owner.parameters.indexOfFirst {
            it.kind == IrParameterKind.ExtensionReceiver
        }] = kClassReference
    }
}

internal fun ComponentTreeEmitter.builderFor(symbol: IrSymbol): DeclarationIrBuilder =
    DeclarationIrBuilder(pluginContext, symbol)

internal fun ComponentTreeEmitter.annotationCall(constructor: IrConstructorSymbol): IrAnnotation =
    DeclarationIrBuilder(pluginContext, constructor).irAnnotation(constructor)

internal fun Type.toIrType(): IrType {
    val boxed = asBoxed()
    val symbol = boxed.declaration.platformModel as? IrClassSymbol
        ?: unsupported("type is not backed by an IrClassSymbol: $this")
    val arguments = boxed.typeArguments.map { it.toIrType() }
    return when {
        arguments.isEmpty() -> symbol.typeWith()
        arguments.size == symbol.owner.typeParameters.size -> symbol.typeWith(arguments)
        else -> symbol.typeWith() // raw type
    }
}

internal fun BindingGraph.componentClassSymbol(): IrClassSymbol =
    model.type.declaration.platformModel as? IrClassSymbol
        ?: unsupported("component type is not backed by an IrClassSymbol: ${model.type}")

internal fun ComponentDependencyModel.dependencyClassSymbol(): IrClassSymbol =
    type.declaration.platformModel as? IrClassSymbol
        ?: unsupported("component dependency type is not backed by an IrClassSymbol: $type")

internal fun ModuleModel.moduleClassSymbol(): IrClassSymbol =
    type.declaration.platformModel as? IrClassSymbol
        ?: unsupported("module type is not backed by an IrClassSymbol: $type")

internal fun parentParams(state: GraphState, parent: BindingGraph): Int =
    state.usedParentsOrdered.indexOf(parent)

internal fun ComponentFactoryModel.InputModel.irInputType(): IrType =
    when (val payload = payload) {
        is ComponentFactoryModel.InputPayload.Instance -> payload.model.type.toIrType()
        is ComponentFactoryModel.InputPayload.Module -> payload.model.moduleClassSymbol().defaultType
        is ComponentFactoryModel.InputPayload.Dependency -> payload.model.dependencyClassSymbol().defaultType
    }
