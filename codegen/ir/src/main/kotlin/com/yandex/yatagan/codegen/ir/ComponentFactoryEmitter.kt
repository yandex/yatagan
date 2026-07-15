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

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.Name

internal fun ComponentTreeEmitter.buildFactoryShell(state: GraphState) {
    val factory = state.graph.model.factory ?: return
    val builderClass = factory.type.declaration.platformModel as? IrClassSymbol
        ?: unsupported("builder type is not backed by an IrClassSymbol: ${factory.type}")
    val factoryImpl = pluginContext.irFactory.buildClass {
        origin = YataganIrOrigin
        name = Name.identifier("ComponentFactoryImpl")
        kind = ClassKind.CLASS
        modality = Modality.FINAL
        visibility = DescriptorVisibilities.PUBLIC
    }.apply {
        createThisReceiverParameter()
        superTypes += builderClass.defaultType
    }
    state.implClass.addChild(factoryImpl)
    state.factoryImpl = factoryImpl

    for (parent in state.usedParentsOrdered) {
        state.factoryParentFields[parent] = factoryImpl.addField {
            name = Name.identifier("fp${state.factoryParentFields.size}")
            type = states.getValue(parent).implClass.defaultType
            visibility = DescriptorVisibilities.PUBLIC
            isFinal = true
            origin = YataganIrOrigin
        }
    }
    for (input in factory.builderInputs) {
        state.factoryBuilderFields[input] = factoryImpl.addField {
            name = Name.identifier("fb${state.factoryBuilderFields.size}")
            type = input.irInputType().makeNullable()
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = false
            origin = YataganIrOrigin
        }
    }
    state.factoryConstructor = factoryImpl.addConstructor {
        visibility = DescriptorVisibilities.PUBLIC
        isPrimary = true
        origin = YataganIrOrigin
    }.apply {
        val params = state.usedParentsOrdered.map { parent ->
            addValueParameter("p${parentParams(state, parent)}", states.getValue(parent).implClass.defaultType)
        }
        body = builderFor(symbol).irBlockBody {
            +irDelegatingConstructorCall(anyConstructor)
            +IrInstanceInitializerCallImpl(startOffset, endOffset, factoryImpl.symbol, irBuiltIns.unitType)
            state.usedParentsOrdered.forEachIndexed { index, parent ->
                +irSetField(
                    irGet(factoryImpl.thisReceiver!!),
                    state.factoryParentFields.getValue(parent),
                    irGet(params[index]),
                )
            }
        }
    }
}

internal fun ComponentTreeEmitter.emitFactoryBodies(state: GraphState) {
    val factory = state.graph.model.factory ?: return
    val factoryImpl = state.factoryImpl ?: return

    for (input in factory.builderInputs) {
        val setterSymbol = input.builderSetter.platformModel as? IrSimpleFunctionSymbol
            ?: unsupported("builder setter is not backed by an IrSimpleFunctionSymbol")
        val override = factoryImpl.addOverride(setterSymbol)
        override.body = builderFor(override.symbol).irBlockBody {
            val field = state.factoryBuilderFields.getValue(input)
            +irSetField(
                irGet(override.dispatchReceiverParameter!!),
                field,
                irAs(irGet(override.regularParameters().single()), field.type),
            )
            if (!input.builderSetter.returnType.isVoid) {
                +irReturn(irAs(irGet(override.dispatchReceiverParameter!!), override.returnType))
            }
        }
    }

    val factoryMethod = factory.factoryMethod
        ?: unsupported("component creator has no factory method: $factory")
    val methodSymbol = factoryMethod.platformModel as? IrSimpleFunctionSymbol
        ?: unsupported("creator factory method is not backed by an IrSimpleFunctionSymbol")
    val override = factoryImpl.addOverride(methodSymbol)
    override.body = builderFor(override.symbol).irBlockBody {
        +irReturn(irCallConstructor(state.constructor.symbol, emptyList()).apply {
            var index = 0
            state.usedParentsOrdered.forEach { parent ->
                arguments[index++] = irGetField(
                    irGet(override.dispatchReceiverParameter!!),
                    state.factoryParentFields.getValue(parent),
                )
            }
            val factoryParams = override.regularParameters()
            factory.factoryInputs.forEachIndexed { paramIndex, _ ->
                arguments[index++] = irGet(factoryParams[paramIndex])
            }
            factory.builderInputs.forEach { input ->
                arguments[index++] = irGetField(
                    irGet(override.dispatchReceiverParameter!!),
                    state.factoryBuilderFields.getValue(input),
                )
            }
        })
    }
}

internal fun ComponentTreeEmitter.emitStaticEntries(state: GraphState) {
    if (!state.graph.isRoot) return
    val componentClass = state.graph.componentClassSymbol().owner
    val factory = state.graph.model.factory
    if (factory != null) {
        val builderClass = factory.type.declaration.platformModel as IrClassSymbol
        state.implClass.addFunction(
            name = "builder",
            returnType = builderClass.defaultType,
            modality = Modality.FINAL,
            visibility = DescriptorVisibilities.PUBLIC,
            isStatic = true,
            origin = YataganIrOrigin,
            startOffset = componentClass.startOffset,
            endOffset = componentClass.endOffset,
        ).apply {
            body = builderFor(symbol).irBlockBody {
                +irReturn(irCallConstructor(state.factoryConstructor.symbol, emptyList()))
            }
        }
    } else {
        val autoBuilderImplConstructor = buildAutoBuilderImpl(state)
        state.implClass.addFunction(
            name = "autoBuilder",
            returnType = symbols.autoBuilderClass.typeWith(state.implClass.symbol.defaultType),
            modality = Modality.FINAL,
            visibility = DescriptorVisibilities.PUBLIC,
            isStatic = true,
            origin = YataganIrOrigin,
            startOffset = componentClass.startOffset,
            endOffset = componentClass.endOffset,
        ).apply {
            body = builderFor(symbol).irBlockBody {
                +irReturn(irCallConstructor(autoBuilderImplConstructor.symbol, emptyList()))
            }
        }
    }
}

private class AutoInput(
    val classSymbol: IrClassSymbol,
    val builderField: IrField,
    val optional: Boolean,
)

/** Builds the nested `AutoBuilderImpl` for a creator-less root and returns its constructor. */
private fun ComponentTreeEmitter.buildAutoBuilderImpl(state: GraphState): IrConstructor {
    val implType = state.implClass.symbol.defaultType
    val autoBuilderType = symbols.autoBuilderClass.typeWith(implType)
    val clazz = pluginContext.irFactory.buildClass {
        origin = YataganIrOrigin
        name = Name.identifier("AutoBuilderImpl")
        kind = ClassKind.CLASS
        modality = Modality.FINAL
        visibility = DescriptorVisibilities.PUBLIC
    }.apply {
        createThisReceiverParameter()
        superTypes += autoBuilderType
    }
    state.implClass.addChild(clazz)

    val inputs = buildList {
        state.dependencyFields.keys.forEach { dependency ->
            add(AutoInput(dependency.dependencyClassSymbol(), builderField = clazz.addField {
                name = Name.identifier("b${size}")
                type = dependency.dependencyClassSymbol().defaultType.makeNullable()
                visibility = DescriptorVisibilities.PRIVATE
                isFinal = false
                origin = YataganIrOrigin
            }, optional = false))
        }
        state.moduleFields.keys.forEach { module ->
            add(AutoInput(module.moduleClassSymbol(), builderField = clazz.addField {
                name = Name.identifier("b${size}")
                type = module.moduleClassSymbol().defaultType.makeNullable()
                visibility = DescriptorVisibilities.PRIVATE
                isFinal = false
                origin = YataganIrOrigin
            }, optional = module.isTriviallyConstructable))
        }
    }

    val constructor = clazz.addConstructor {
        visibility = DescriptorVisibilities.PUBLIC
        isPrimary = true
        origin = YataganIrOrigin
    }.apply {
        body = builderFor(symbol).irBlockBody {
            +irDelegatingConstructorCall(anyConstructor)
            +IrInstanceInitializerCallImpl(startOffset, endOffset, clazz.symbol, irBuiltIns.unitType)
        }
    }

    val interfaceProvideInput = symbols.autoBuilderClass.owner.functions.single {
        it.name.asString() == "provideInput" && it.regularParameters().size == 2
    }
    clazz.addFunction(
        name = "provideInput",
        returnType = autoBuilderType,
        modality = Modality.FINAL,
        visibility = DescriptorVisibilities.PUBLIC,
        origin = YataganIrOrigin,
    ).apply {
        val inputTypeParameter = addTypeParameter("I", irBuiltIns.anyType)
        val inputParam = addValueParameter("input", inputTypeParameter.defaultType)
        val clazzParam = addValueParameter(
            "clazz", symbols.javaLangClass.typeWith(inputTypeParameter.defaultType))
        overriddenSymbols = listOf(interfaceProvideInput.symbol)
        body = builderFor(symbol).irBlockBody {
            val receiver = dispatchReceiverParameter!!
            val reportCall = {
                irCall(symbols.reportUnexpectedAutoBuilderInput).apply {
                    val offset = regularArgumentOffset(symbols.reportUnexpectedAutoBuilderInput.owner)
                    arguments[offset] = irGet(clazzParam)
                    arguments[offset + 1] = irCall(symbols.listOfVararg).apply {
                        val classStarType = symbols.javaLangClass.typeWith(irBuiltIns.anyNType)
                        typeArguments[0] = classStarType
                        arguments[regularArgumentOffset(symbols.listOfVararg.owner)] = IrVarargImpl(
                            startOffset, endOffset,
                            irBuiltIns.arrayClass.typeWith(classStarType),
                            classStarType,
                            inputs.map { irJavaClassLiteral(this@irBlockBody, it.classSymbol) },
                        )
                    }
                }
            }
            if (inputs.isEmpty()) {
                +reportCall()
            } else {
                val branches = inputs.map { input ->
                    irBranch(
                        irEquals(irGet(clazzParam), irJavaClassLiteral(this, input.classSymbol)),
                        irSetField(irGet(receiver), input.builderField,
                            irAs(irGet(inputParam), input.builderField.type)),
                    )
                } + irElseBranch(reportCall())
                +irWhen(irBuiltIns.unitType, branches)
            }
            +irReturn(irAs(irGet(receiver), autoBuilderType))
        }
    }

    clazz.addFunction(
        name = "create",
        returnType = implType,
        modality = Modality.FINAL,
        visibility = DescriptorVisibilities.PUBLIC,
        origin = YataganIrOrigin,
    ).apply {
        overriddenSymbols = listOf(symbols.autoBuilderClass.owner.functions.single {
            it.name.asString() == "create"
        }.symbol)
        body = builderFor(symbol).irBlockBody {
            val receiver = dispatchReceiverParameter!!
            for (input in inputs) {
                if (input.optional) continue
                +irIfThen(
                    irBuiltIns.unitType,
                    irEqualsNull(irGetField(irGet(receiver), input.builderField)),
                    irCall(symbols.reportMissingAutoBuilderInput).apply {
                        arguments[regularArgumentOffset(symbols.reportMissingAutoBuilderInput.owner)] =
                            irJavaClassLiteral(this@irBlockBody, input.classSymbol)
                    },
                )
            }
            +irReturn(irCallConstructor(state.constructor.symbol, emptyList()).apply {
                inputs.forEachIndexed { index, input ->
                    arguments[index] = irGetField(irGet(receiver), input.builderField)
                }
            })
        }
    }
    return constructor
}
