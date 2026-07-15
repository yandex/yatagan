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
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
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
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.name.Name

internal fun ComponentTreeEmitter.buildProviderClass(state: GraphState, caching: Boolean): ProviderClass {
    val multiThread = state.graph.requiresSynchronizedAccess
    val clazz = pluginContext.irFactory.buildClass {
        origin = YataganIrOrigin
        name = Name.identifier(if (caching) "CachingProviderImpl" else "ProviderImpl")
        kind = ClassKind.CLASS
        modality = Modality.FINAL
        visibility = DescriptorVisibilities.PUBLIC
    }.apply {
        createThisReceiverParameter()
        superTypes += symbols.lazyClass.typeWith(irBuiltIns.anyNType)
    }
    state.implClass.addChild(clazz)

    val delegateField = clazz.addField {
        name = Name.identifier("delegate")
        type = state.implClass.defaultType
        visibility = DescriptorVisibilities.PRIVATE
        isFinal = true
        origin = YataganIrOrigin
    }
    val indexField = clazz.addField {
        name = Name.identifier("index")
        type = irBuiltIns.intType
        visibility = DescriptorVisibilities.PRIVATE
        isFinal = true
        origin = YataganIrOrigin
    }
    val valueField = if (caching) {
        clazz.addField {
            name = Name.identifier("value")
            type = irBuiltIns.anyNType
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = false
            origin = YataganIrOrigin
        }.also { field ->
            if (multiThread) {
                field.annotations += annotationCall(symbols.volatileAnnotationConstructor)
            }
        }
    } else null

    val constructor = clazz.addConstructor {
        visibility = DescriptorVisibilities.PUBLIC
        isPrimary = true
        origin = YataganIrOrigin
    }.apply {
        val delegateParam = addValueParameter("delegate", state.implClass.defaultType)
        val indexParam = addValueParameter("index", irBuiltIns.intType)
        body = builderFor(symbol).irBlockBody {
            +irDelegatingConstructorCall(anyConstructor)
            +IrInstanceInitializerCallImpl(startOffset, endOffset, clazz.symbol, irBuiltIns.unitType)
            +irSetField(irGet(clazz.thisReceiver!!), delegateField, irGet(delegateParam))
            +irSetField(irGet(clazz.thisReceiver!!), indexField, irGet(indexParam))
        }
    }

    val getFunction = clazz.addFunction(
        name = "get",
        returnType = irBuiltIns.anyNType,
        modality = Modality.FINAL,
        visibility = DescriptorVisibilities.PUBLIC,
        origin = YataganIrOrigin,
    ).apply {
        overriddenSymbols = listOf(symbols.providerGet)
    }
    val getSlowFunction = if (caching) {
        clazz.addFunction(
            name = "getSlow",
            returnType = irBuiltIns.anyNType,
            modality = Modality.FINAL,
            visibility = DescriptorVisibilities.PRIVATE,
            origin = YataganIrOrigin,
        ).also { slow ->
            if (multiThread) {
                slow.annotations += annotationCall(symbols.synchronizedAnnotationConstructor)
            }
        }
    } else null

    return ProviderClass(constructor, delegateField, indexField, valueField, getFunction, getSlowFunction)
}

internal fun ComponentTreeEmitter.emitCachePair(state: GraphState, binding: Binding) {
    val field = state.cacheFields.getValue(binding)
    val fast = state.accessors.getValue(binding)
    val slow = state.cacheSlowMethods.getValue(binding)

    fast.body = builderFor(fast.symbol).irBlockBody {
        val receiver = fast.dispatchReceiverParameter!!
        val local = irTemporary(irGetField(irGet(receiver), field), nameHint = "local", isMutable = true)
        +irIfThen(
            irBuiltIns.unitType,
            irEqualsNull(irGet(local)),
            irSet(local, irCall(slow.symbol).apply { dispatchReceiver = irGet(receiver) }),
        )
        +irReturn(irGet(local))
    }

    slow.body = builderFor(slow.symbol).irBlockBody {
        val receiver = slow.dispatchReceiverParameter!!
        val local = irTemporary(irGetField(irGet(receiver), field), nameHint = "local", isMutable = true)
        +irIfThen(
            irBuiltIns.unitType,
            irEqualsNull(irGet(local)),
            irBlock(resultType = irBuiltIns.unitType) {
                threadAssertionCall(this, state)?.let { +it }
                +irSet(local, creationExpression(this, state, binding, thisExpr = { irGet(receiver) }))
                +irSetField(irGet(receiver), field, irGet(local))
            },
        )
        +irReturn(irGet(local))
    }
}

internal fun ComponentTreeEmitter.emitSwitch(state: GraphState, switchFn: IrSimpleFunction) {
    val slotParam = switchFn.regularParameters().single()
    switchFn.body = builderFor(switchFn.symbol).irBlockBody {
        val branches = state.slots.map { (binding, slot) ->
            irBranch(
                irEquals(irGet(slotParam), irInt(slot)),
                directAccess(this, state, { irGet(switchFn.dispatchReceiverParameter!!) }, binding),
            )
        } + irElseBranch(
            IrThrowImpl(
                startOffset, endOffset, irBuiltIns.nothingType,
                irCallConstructor(symbols.assertionErrorConstructor, emptyList()),
            ),
        )
        +irReturn(irWhen(irBuiltIns.anyNType, branches))
    }
}

internal fun ComponentTreeEmitter.emitProviderBodies(
    state: GraphState,
    provider: ProviderClass,
    caching: Boolean,
) {
    val switchFn = state.switchFunction!!
    fun IrBuilderWithScope.switchCall(thisParam: IrValueParameter): IrExpression =
        irCall(switchFn.symbol).apply {
            dispatchReceiver = irGetField(irGet(thisParam), provider.delegateField)
            arguments[regularArgumentOffset(switchFn)] = irGetField(irGet(thisParam), provider.indexField)
        }

    if (!caching) {
        provider.getFunction.body = builderFor(provider.getFunction.symbol).irBlockBody {
            +irReturn(switchCall(provider.getFunction.dispatchReceiverParameter!!))
        }
        return
    }

    val valueField = provider.valueField!!
    val slow = provider.getSlowFunction!!

    provider.getFunction.body = builderFor(provider.getFunction.symbol).irBlockBody {
        val thisParam = provider.getFunction.dispatchReceiverParameter!!
        val local = irTemporary(irGetField(irGet(thisParam), valueField), nameHint = "local", isMutable = true)
        +irIfThen(
            irBuiltIns.unitType,
            irEqualsNull(irGet(local)),
            irSet(local, irCall(slow.symbol).apply { dispatchReceiver = irGet(thisParam) }),
        )
        +irReturn(irGet(local))
    }

    slow.body = builderFor(slow.symbol).irBlockBody {
        val thisParam = slow.dispatchReceiverParameter!!
        val local = irTemporary(irGetField(irGet(thisParam), valueField), nameHint = "local", isMutable = true)
        +irIfThen(
            irBuiltIns.unitType,
            irEqualsNull(irGet(local)),
            irBlock(resultType = irBuiltIns.unitType) {
                threadAssertionCall(this, state)?.let { +it }
                +irSet(local, switchCall(thisParam))
                +irSetField(irGet(thisParam), valueField, irGet(local))
            },
        )
        +irReturn(irGet(local))
    }
}

internal fun ComponentTreeEmitter.emitOptionalAccessor(
    state: GraphState,
    binding: Binding,
    kind: DependencyKind,
    accessor: IrSimpleFunction,
) {
    val underlyingKind = when (kind) {
        DependencyKind.Optional -> DependencyKind.Direct
        DependencyKind.OptionalLazy -> DependencyKind.Lazy
        DependencyKind.OptionalProvider -> DependencyKind.Provider
        else -> error("not reached")
    }
    accessor.body = builderFor(accessor.symbol).irBlockBody {
        val thisExpr = { irGet(accessor.dispatchReceiverParameter!!) }
        val ofCall = {
            irCall(symbols.optionalOf.symbol).apply {
                dispatchReceiver = irGetObject(symbols.optionalCompanion.symbol)
                typeArguments[0] = irBuiltIns.anyType
                arguments[regularArgumentOffset(symbols.optionalOf)] = irAs(
                    accessKind(
                        builder = this@irBlockBody,
                        inside = state,
                        thisExpr = thisExpr,
                        binding = binding,
                        kind = underlyingKind,
                    ),
                    irBuiltIns.anyType,
                )
            }
        }
        val emptyCall = {
            irCall(symbols.optionalEmpty.symbol).apply {
                dispatchReceiver = irGetObject(symbols.optionalCompanion.symbol)
                typeArguments[0] = irBuiltIns.anyType
            }
        }
        when (val scope = binding.conditionScope) {
            ConditionScope.Always -> +irReturn(ofCall())

            is ConditionScope.ExpressionScope -> +irReturn(irIfThenElse(
                accessor.returnType,
                conditionExpression(this, state, thisExpr, scope),
                ofCall(),
                emptyCall(),
            ))

            else -> +irReturn(emptyCall())
        }
    }
}

internal fun IrBuilderWithScope.newProvider(
    provider: ProviderClass,
    component: IrExpression,
    slot: Int,
): IrExpression = irCallConstructor(provider.constructor.symbol, emptyList()).apply {
    arguments[0] = component
    arguments[1] = irInt(slot)
}

private fun ComponentTreeEmitter.threadAssertionCall(
    builder: IrBuilderWithScope,
    state: GraphState,
): IrExpression? {
    if (state.graph.requiresSynchronizedAccess) return null
    val method = options.threadChecker?.assertThreadAccessMethod ?: return null
    val symbol = method.platformModel as? IrSimpleFunctionSymbol ?: return null
    return builder.irCall(symbol).apply {
        if (symbol.owner.parameters.any { it.kind == IrParameterKind.DispatchReceiver }) {
            // Kotlin `object`s and (`@JvmStatic`) companion members dispatch on the object instance.
            val parentClass = symbol.owner.parent as? IrClass
            if (parentClass?.isObject != true) {
                unsupported("thread checker method `$method` is neither static nor an object member")
            }
            dispatchReceiver = builder.irGetObject(parentClass.symbol)
        }
    }
}
