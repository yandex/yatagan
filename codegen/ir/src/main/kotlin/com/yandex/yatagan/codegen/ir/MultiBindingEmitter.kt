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

import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.model.CollectionTargetKind
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.lang.Annotation as LangAnnotation
import com.yandex.yatagan.lang.Type
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irChar
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irShort
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType

internal fun ComponentTreeEmitter.multiBindingExpression(
    builder: IrBuilderWithScope,
    owner: GraphState,
    binding: MultiBinding,
    thisExpr: () -> IrExpression,
): IrExpression {
    val collectionClass = when (binding.kind) {
        CollectionTargetKind.List -> symbols.arrayListClass
        CollectionTargetKind.Set -> symbols.hashSetClass
    }
    val capacityConstructor = collectionClass.owner.constructors.single { constructor ->
        constructor.regularParameters().singleOrNull()?.type?.isInt() == true
    }
    return builder.irBlock(resultType = irBuiltIns.anyNType) {
        val collection = irTemporary(
            irCallConstructor(capacityConstructor.symbol, listOf(irBuiltIns.anyNType)).apply {
                arguments[capacityConstructor.parameters.indexOf(
                    capacityConstructor.regularParameters().single())] = irInt(binding.contributions.size)
            },
            nameHint = "c",
        )
        binding.upstream?.let { upstream ->
            val upstreamAccess = accessKind(
                builder = this, inside = owner, thisExpr = thisExpr,
                binding = upstream, kind = DependencyKind.Direct,
            )
            +irCall(symbols.mutableCollectionAddAll).apply {
                dispatchReceiver = irGet(collection)
                arguments[regularArgumentOffset(symbols.mutableCollectionAddAll.owner)] =
                    irAs(upstreamAccess, irBuiltIns.collectionClass.typeWith(irBuiltIns.anyNType))
            }
        }
        for ((node, contributionType) in binding.contributions) {
            val contributionBinding = binding.owner.resolveBinding(node)
            if (contributionBinding.conditionScope == ConditionScope.Never) continue
            val value = accessKind(
                builder = this, inside = owner, thisExpr = thisExpr,
                binding = contributionBinding, kind = DependencyKind.Direct,
            )
            val addCall = when (contributionType) {
                MultiBinding.ContributionType.Element -> irCall(symbols.mutableCollectionAdd).apply {
                    dispatchReceiver = irGet(collection)
                    arguments[regularArgumentOffset(symbols.mutableCollectionAdd.owner)] = value
                }

                MultiBinding.ContributionType.Collection -> irCall(symbols.mutableCollectionAddAll).apply {
                    dispatchReceiver = irGet(collection)
                    arguments[regularArgumentOffset(symbols.mutableCollectionAddAll.owner)] =
                        irAs(value, irBuiltIns.collectionClass.typeWith(irBuiltIns.anyNType))
                }
            }
            when (val scope = contributionBinding.conditionScope) {
                is ConditionScope.ExpressionScope ->
                    +irIfThen(irBuiltIns.unitType, conditionExpression(this, owner, thisExpr, scope), addCall)

                else -> +addCall
            }
        }
        +irGet(collection)
    }
}

internal fun ComponentTreeEmitter.mapBindingExpression(
    builder: IrBuilderWithScope,
    owner: GraphState,
    binding: MapBinding,
    thisExpr: () -> IrExpression,
): IrExpression {
    val capacityConstructor = symbols.hashMapClass.owner.constructors.single { constructor ->
        constructor.regularParameters().singleOrNull()?.type?.isInt() == true
    }
    return builder.irBlock(resultType = irBuiltIns.anyNType) {
        val map = irTemporary(
            irCallConstructor(
                capacityConstructor.symbol,
                listOf(irBuiltIns.anyNType, irBuiltIns.anyNType),
            ).apply {
                arguments[capacityConstructor.parameters.indexOf(
                    capacityConstructor.regularParameters().single())] = irInt(binding.contents.size)
            },
            nameHint = "m",
        )
        binding.upstream?.let { upstream ->
            val upstreamAccess = accessKind(
                builder = this, inside = owner, thisExpr = thisExpr,
                binding = upstream, kind = DependencyKind.Direct,
            )
            +irCall(symbols.mutableMapPutAll).apply {
                dispatchReceiver = irGet(map)
                arguments[regularArgumentOffset(symbols.mutableMapPutAll.owner)] = irAs(
                    upstreamAccess,
                    irBuiltIns.mapClass.typeWith(irBuiltIns.anyNType, irBuiltIns.anyNType),
                )
            }
        }
        for (contribution in binding.contents) {
            val contributionBinding = binding.owner.resolveBinding(contribution.dependency.node)
            if (contributionBinding.conditionScope == ConditionScope.Never) continue
            val putCall = irCall(symbols.mutableMapPut).apply {
                dispatchReceiver = irGet(map)
                val offset = regularArgumentOffset(symbols.mutableMapPut.owner)
                arguments[offset] = annotationValueLiteral(this@irBlock, contribution.keyValue)
                arguments[offset + 1] = access(
                    builder = this@irBlock, inside = owner, thisExpr = thisExpr,
                    dependency = contribution.dependency,
                )
            }
            when (val scope = contributionBinding.conditionScope) {
                is ConditionScope.ExpressionScope ->
                    +irIfThen(irBuiltIns.unitType, conditionExpression(this@irBlock, owner, thisExpr, scope), putCall)

                else -> +putCall
            }
        }
        +irGet(map)
    }
}

private fun ComponentTreeEmitter.annotationValueLiteral(
    builder: IrBuilderWithScope,
    value: LangAnnotation.Value,
): IrExpression = value.accept(object : LangAnnotation.Value.Visitor<IrExpression> {
    override fun visitDefault(value: Any?): IrExpression =
        unsupported("unsupported annotation value used as a map key: $value")

    override fun visitBoolean(value: Boolean) = builder.irBoolean(value)
    override fun visitByte(value: Byte) = builder.irByte(value)
    override fun visitShort(value: Short) = builder.irShort(value)
    override fun visitInt(value: Int) = builder.irInt(value)
    override fun visitLong(value: Long) = builder.irLong(value)
    override fun visitChar(value: Char) = builder.irChar(value)
    override fun visitFloat(value: Float): IrExpression =
        IrConstImpl.float(builder.startOffset, builder.endOffset, irBuiltIns.floatType, value)

    override fun visitDouble(value: Double): IrExpression =
        IrConstImpl.double(builder.startOffset, builder.endOffset, irBuiltIns.doubleType, value)

    override fun visitString(value: String) = builder.irString(value)

    override fun visitType(value: Type): IrExpression {
        val classSymbol = value.declaration.platformModel as? IrClassSymbol
            ?: unsupported("class map key is not backed by an IrClassSymbol: $value")
        return irJavaClassLiteral(builder, classSymbol)
    }

    override fun visitEnumConstant(enum: Type, constant: String): IrExpression {
        val enumClass = enum.declaration.platformModel as? IrClassSymbol
            ?: unsupported("enum map key is not backed by an IrClassSymbol: $enum")
        val entry = enumClass.owner.declarations.filterIsInstance<IrEnumEntry>()
            .singleOrNull { it.name.asString() == constant }
            ?: unsupported("enum entry $constant is missing in $enum")
        return IrGetEnumValueImpl(
            builder.startOffset, builder.endOffset, enumClass.defaultType, entry.symbol,
        )
    }
})
