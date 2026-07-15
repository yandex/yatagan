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
import com.yandex.yatagan.core.model.BooleanExpression
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.lang.Field as LangField
import com.yandex.yatagan.lang.Member as LangMember
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irFalse
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isBoolean

internal fun ComponentTreeEmitter.emitLazyLiteralAccessors(state: GraphState) {
    for ((literal, accessor) in state.lazyLiteralAccessors) {
        val field = state.lazyLiteralFields.getValue(literal)
        accessor.body = builderFor(accessor.symbol).irBlockBody {
            val receiver = accessor.dispatchReceiverParameter!!
            // Tri-state: 0 = uninitialized, 1 = true, 2 = false. Deliberately unsynchronized -
            // duplicate computation is tolerated (matches the reference backends).
            +irIfThen(
                irBuiltIns.unitType,
                irEquals(irGetField(irGet(receiver), field), irInt(0)),
                irSetField(irGet(receiver), field, irIfThenElse(
                    irBuiltIns.intType,
                    literalEvaluation(this, state, { irGet(receiver) }, literal),
                    irInt(1),
                    irInt(2),
                )),
            )
            +irReturn(irEquals(irGetField(irGet(receiver), field), irInt(1)))
        }
    }
}

internal fun ComponentTreeEmitter.literalEvaluation(
    builder: IrBuilderWithScope,
    owner: GraphState,
    thisExpr: () -> IrExpression,
    literal: ConditionModel,
): IrExpression {
    var value: IrExpression? = if (literal.requiresInstance) {
        val rootBinding = owner.graph.resolveBinding(literal.root)
        builder.irAs(
            accessKind(builder, owner, thisExpr, rootBinding, DependencyKind.Direct),
            literal.root.type.toIrType(),
        )
    } else {
        null // the first path member is static or an object access
    }
    for (member in literal.path) {
        value = memberAccess(builder, value, member)
    }
    val result = value ?: unsupported("empty condition path: $literal")
    return if (result.type.isBoolean()) result else builder.irAs(result, irBuiltIns.booleanType)
}

private fun memberAccess(
    builder: IrBuilderWithScope,
    receiver: IrExpression?,
    member: LangMember,
): IrExpression = when (val platformModel = member.platformModel) {
    is IrFieldSymbol -> {
        // Same cross-file synthetic accessor hazard as in member injection: read Kotlin
        // properties through their getter, touch the field directly only when there is none
        // (Java fields, @JvmField).
        val getter = platformModel.owner.correspondingPropertySymbol?.owner?.getter
        if (getter != null) {
            builder.irCall(getter.symbol).apply {
                if (getter.parameters.any { it.kind == IrParameterKind.DispatchReceiver }) {
                    dispatchReceiver = receiver
                        ?: builder.irGetObject((getter.parent as IrClass).symbol)
                }
            }
        } else {
            builder.irGetField(receiver, platformModel.owner)
        }
    }

    is IrSimpleFunctionSymbol -> builder.irCall(platformModel).apply {
        if (platformModel.owner.parameters.any { it.kind == IrParameterKind.DispatchReceiver }) {
            dispatchReceiver = receiver
                ?: builder.irGetObject((platformModel.owner.parent as IrClass).symbol)
        }
    }

    null -> {
        // KCP lang synthesizes object INSTANCE/Companion members without an IR counterpart.
        val objectSymbol = (member as? LangField)?.type?.declaration?.platformModel as? IrClassSymbol
            ?: unsupported("synthetic member $member is not an object access")
        builder.irGetObject(objectSymbol)
    }

    else -> unsupported("condition path member $member is not backed by an IR symbol")
}

internal fun ComponentTreeEmitter.literalAccess(
    builder: IrBuilderWithScope,
    inside: GraphState,
    thisExpr: () -> IrExpression,
    literal: ConditionModel,
): IrExpression {
    val ownerGraph = generateSequence(inside.graph) { it.parent }
        .firstOrNull { literal in it.localConditionLiterals }
        ?: unsupported("no graph hosts condition literal: $literal")
    val ownerState = states.getValue(ownerGraph)
    val component = componentExpression(builder, inside, thisExpr, ownerState)
    ownerState.eagerLiteralFields[literal]?.let { field ->
        return builder.irGetField(component, field)
    }
    return builder.irCall(ownerState.lazyLiteralAccessors.getValue(literal).symbol).apply {
        dispatchReceiver = component
    }
}

internal fun ComponentTreeEmitter.conditionExpression(
    builder: IrBuilderWithScope,
    inside: GraphState,
    thisExpr: () -> IrExpression,
    scope: ConditionScope.ExpressionScope,
): IrExpression {
    val visitor = object : BooleanExpression.Visitor<IrExpression> {
        override fun visitVariable(variable: BooleanExpression.Variable): IrExpression =
            literalAccess(builder, inside, thisExpr, variable.model)

        override fun visitNot(not: BooleanExpression.Not): IrExpression {
            val visitor = this
            return builder.irCall(irBuiltIns.booleanNotSymbol).apply {
                dispatchReceiver = not.underlying.accept(visitor)
            }
        }

        override fun visitAnd(and: BooleanExpression.And): IrExpression = builder.irIfThenElse(
            irBuiltIns.booleanType, and.lhs.accept(this), and.rhs.accept(this), builder.irFalse())

        override fun visitOr(or: BooleanExpression.Or): IrExpression = builder.irIfThenElse(
            irBuiltIns.booleanType, or.lhs.accept(this), builder.irTrue(), or.rhs.accept(this))
    }
    return scope.expression.accept(visitor)
}
