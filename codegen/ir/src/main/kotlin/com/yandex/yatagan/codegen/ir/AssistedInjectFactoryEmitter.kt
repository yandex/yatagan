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

@file:OptIn(Incubating::class, UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.codegen.ir

import com.yandex.yatagan.base.api.Incubating
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.ConditionScope
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name

internal fun ComponentTreeEmitter.buildAssistedFactoryShells(state: GraphState) {
    for ((model, conditionScope) in state.graph.localAssistedInjectFactories) {
        if (conditionScope == ConditionScope.Never) continue
        val factoryInterface = model.type.declaration.platformModel as? IrClassSymbol
            ?: unsupported("assisted factory type is not backed by an IrClassSymbol: ${model.type}")
        val factoryType = model.type.toIrType() as? IrSimpleType
            ?: unsupported("assisted factory type is not a simple type: ${model.type}")
        val substitution = if (factoryInterface.owner.typeParameters.isEmpty()) emptyMap() else {
            if (factoryType.arguments.size != factoryInterface.owner.typeParameters.size) {
                unsupported("raw generic assisted factories are not supported: ${model.type}")
            }
            factoryInterface.owner.typeParameters.map { it.symbol }.zip(
                factoryType.arguments.map { argument ->
                    (argument as? IrTypeProjection)?.type
                        ?: unsupported("star-projected assisted factories are not supported: ${model.type}")
                }
            ).toMap()
        }
        val clazz = pluginContext.irFactory.buildClass {
            origin = YataganIrOrigin
            name = childImplementationName(state.implClass, factoryInterface.owner)
            kind = ClassKind.CLASS
            modality = Modality.FINAL
            visibility = DescriptorVisibilities.PUBLIC
        }.apply {
            createThisReceiverParameter()
            superTypes += factoryType
        }
        state.implClass.addChild(clazz)
        val delegateField = clazz.addField {
            name = Name.identifier("delegate")
            type = state.implClass.defaultType
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
            origin = YataganIrOrigin
        }
        val constructor = clazz.addConstructor {
            visibility = DescriptorVisibilities.PUBLIC
            isPrimary = true
            origin = YataganIrOrigin
        }.apply {
            val delegateParam = addValueParameter("delegate", state.implClass.defaultType)
            body = builderFor(symbol).irBlockBody {
                +irDelegatingConstructorCall(anyConstructor)
                +IrInstanceInitializerCallImpl(startOffset, endOffset, clazz.symbol, irBuiltIns.unitType)
                +irSetField(irGet(clazz.thisReceiver!!), delegateField, irGet(delegateParam))
            }
        }
        state.assistedFactoryImpls[model] = AssistedFactoryImplState(constructor, delegateField, clazz, substitution)
    }
}

internal fun ComponentTreeEmitter.emitAssistedFactoryBodies(state: GraphState) {
    for ((model, impl) in state.assistedFactoryImpls) {
        val factoryMethodSymbol = model.factoryMethod!!.platformModel as IrSimpleFunctionSymbol
        val constructorSymbol = model.assistedInjectConstructor!!.platformModel as IrConstructorSymbol
        val constructor = constructorSymbol.owner
        val override = impl.clazz.addOverride(factoryMethodSymbol, impl.substitution)
        // The override's return type is the concrete constructee type (factory type arguments
        // already substituted in), so derive the constructor's type arguments from it.
        val constructedClass = constructor.parent as IrClass
        val (typeArguments, substitution) = if (constructedClass.typeParameters.isEmpty()) {
            emptyList<IrType>() to emptyMap()
        } else {
            val targetIrType = override.returnType as? IrSimpleType
                ?: unsupported("assisted factory return type is not a simple type: $model")
            if (targetIrType.arguments.size != constructedClass.typeParameters.size) {
                unsupported("raw generic construction targets are not supported: $model")
            }
            val arguments = targetIrType.arguments.map { argument ->
                (argument as? IrTypeProjection)?.type
                    ?: unsupported("star-projected construction targets are not supported: $model")
            }
            arguments to constructedClass.typeParameters.map { it.symbol }.zip(arguments).toMap()
        }
        val constructorParameters = constructor.regularParameters()
        if (constructorParameters.size != model.assistedConstructorParameters.size) {
            unsupported("assisted constructor parameter count mismatch: $model")
        }
        val factoryParams = override.regularParameters()
        if (factoryParams.size != model.assistedFactoryParameters.size) {
            unsupported("assisted factory parameter count mismatch: $model")
        }
        override.body = builderFor(override.symbol).irBlockBody {
            +irReturn(irCallConstructor(constructorSymbol, typeArguments).apply {
                model.assistedConstructorParameters.forEachIndexed { index, parameter ->
                    val constructorParam = constructorParameters[index]
                    arguments[constructor.parameters.indexOf(constructorParam)] = when (parameter) {
                        is AssistedInjectFactoryModel.Parameter.Assisted -> {
                            val factoryIndex = model.assistedFactoryParameters.indexOf(parameter)
                            if (factoryIndex < 0) {
                                unsupported("no matching @Assisted factory parameter for $parameter: $model")
                            }
                            irGet(factoryParams[factoryIndex])
                        }

                        is AssistedInjectFactoryModel.Parameter.Injected -> access(
                            builder = this@irBlockBody,
                            inside = state,
                            thisExpr = {
                                irGetField(irGet(override.dispatchReceiverParameter!!), impl.delegateField)
                            },
                            dependency = parameter.dependency,
                            expectedType = constructorParam.type.substituteTypeParameters(substitution),
                        )
                    }
                }
            })
        }
    }
}
