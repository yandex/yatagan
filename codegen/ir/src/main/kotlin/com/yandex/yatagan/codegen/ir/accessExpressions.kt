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
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyBinding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.graph.bindings.ComponentInstanceBinding
import com.yandex.yatagan.core.graph.bindings.ConditionExpressionValueBinding
import com.yandex.yatagan.core.graph.bindings.EmptyBinding
import com.yandex.yatagan.core.graph.bindings.InstanceBinding
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding
import com.yandex.yatagan.core.graph.bindings.SubComponentFactoryBinding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.lang.Method
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irFalse
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isObject

internal fun ComponentTreeEmitter.access(
    builder: IrBuilderWithScope,
    inside: GraphState,
    thisExpr: () -> IrExpression,
    dependency: NodeDependency,
    expectedType: IrType? = null,
): IrExpression {
    val (node, kind) = dependency
    val binding = inside.graph.resolveBinding(node)
    val raw = accessKind(builder, inside, thisExpr, binding, kind)
    return if (expectedType != null) builder.irAs(raw, expectedType) else raw
}

internal fun ComponentTreeEmitter.accessKind(
    builder: IrBuilderWithScope,
    inside: GraphState,
    thisExpr: () -> IrExpression,
    binding: Binding,
    kind: DependencyKind,
): IrExpression {
    val owner = states.getValue(binding.owner)
    val comp = { componentExpression(builder, inside, thisExpr, owner) }
    return when (kind) {
        DependencyKind.Direct -> directAccessOn(builder, owner, comp, binding)

        DependencyKind.Provider -> builder.newProvider(owner.providerImpl!!, comp(), owner.slotFor(binding))

        DependencyKind.Lazy -> if (binding.scopes.isNotEmpty()) {
            builder.newProvider(owner.providerImpl!!, comp(), owner.slotFor(binding))
        } else {
            builder.newProvider(owner.cachingProviderImpl!!, comp(), owner.slotFor(binding))
        }

        DependencyKind.Optional, DependencyKind.OptionalLazy, DependencyKind.OptionalProvider ->
            builder.irCall(owner.optionalAccessors.getValue(binding to kind).symbol).apply {
                dispatchReceiver = comp()
            }
    }
}

private fun ComponentTreeEmitter.directAccessOn(
    builder: IrBuilderWithScope,
    owner: GraphState,
    comp: () -> IrExpression,
    binding: Binding,
): IrExpression = when (binding) {
    is InstanceBinding -> builder.irGetField(comp(), owner.instanceFields.getValue(binding.target))
    is ComponentDependencyBinding ->
        builder.irGetField(comp(), owner.dependencyFields.getValue(binding.dependency))
    is ComponentInstanceBinding -> comp()
    is EmptyBinding -> unsupported("direct access to a missing binding: $binding")
    else -> builder.irCall(owner.accessors.getValue(binding).symbol).apply {
        dispatchReceiver = comp()
    }
}

internal fun ComponentTreeEmitter.directAccess(
    builder: IrBuilderWithScope,
    inside: GraphState,
    thisExpr: () -> IrExpression,
    binding: Binding,
): IrExpression {
    val owner = states.getValue(binding.owner)
    return directAccessOn(builder, owner, { componentExpression(builder, inside, thisExpr, owner) }, binding)
}

internal fun ComponentTreeEmitter.componentExpression(
    builder: IrBuilderWithScope,
    inside: GraphState,
    thisExpr: () -> IrExpression,
    target: GraphState,
): IrExpression {
    if (inside === target) return thisExpr()
    val field = inside.parentFields[target.graph]
        ?: unsupported("no parent reference from ${inside.graph} to ${target.graph}")
    return builder.irGetField(thisExpr(), field)
}

/** Creation expression for a binding; only ever emitted inside the binding owner's impl. */
internal fun ComponentTreeEmitter.creationExpression(
    builder: IrBuilderWithScope,
    owner: GraphState,
    binding: Binding,
    thisExpr: () -> IrExpression,
): IrExpression = when (binding) {
    is ProvisionBinding -> provisionExpression(builder, owner, binding, thisExpr)

    is SubComponentFactoryBinding -> {
        val childState = states.getValue(binding.targetGraph)
        builder.irCallConstructor(childState.factoryConstructor.symbol, emptyList()).apply {
            childState.usedParentsOrdered.forEachIndexed { index, parent ->
                arguments[index] = componentExpression(builder, owner, thisExpr, states.getValue(parent))
            }
        }
    }

    is ComponentDependencyEntryPointBinding -> {
        val getterSymbol = binding.getter.platformModel as? IrSimpleFunctionSymbol
            ?: unsupported("dependency entry point is not backed by an IrSimpleFunctionSymbol: $binding")
        builder.irCall(getterSymbol).apply {
            dispatchReceiver = builder.irGetField(
                thisExpr(),
                owner.dependencyFields.getValue(binding.dependency),
            )
        }
    }

    is MultiBinding -> multiBindingExpression(builder, owner, binding, thisExpr)

    is MapBinding -> mapBindingExpression(builder, owner, binding, thisExpr)

    is AssistedInjectFactoryBinding -> {
        var host: BindingGraph? = binding.owner
        while (host != null && binding.model !in states.getValue(host).assistedFactoryImpls) {
            host = host.parent
        }
        val hostState = states.getValue(
            host ?: unsupported("no assisted factory implementation found for: $binding"))
        val impl = hostState.assistedFactoryImpls.getValue(binding.model)
        builder.irCallConstructor(impl.constructor.symbol, emptyList()).apply {
            arguments[0] = componentExpression(builder, owner, thisExpr, hostState)
        }
    }

    is AlternativesBinding -> alternativesExpression(builder, owner, binding, thisExpr)

    is ConditionExpressionValueBinding ->
        binding.model.expression?.let { conditionExpression(builder, owner, thisExpr, it) }
            ?: builder.irFalse()

    else -> unsupported("unsupported binding kind ${binding.javaClass.simpleName}: $binding")
}

private fun ComponentTreeEmitter.provisionExpression(
    builder: IrBuilderWithScope,
    owner: GraphState,
    binding: ProvisionBinding,
    thisExpr: () -> IrExpression,
): IrExpression {
    val inputs = binding.inputs
    return when (val platformModel = binding.provision.platformModel) {
        is IrConstructorSymbol -> {
            val constructor = platformModel.owner
            val clazz = constructor.parent as IrClass
            val typeArguments: List<IrType> = if (clazz.typeParameters.isEmpty()) {
                emptyList()
            } else {
                val targetType = binding.target.type.toIrType() as? IrSimpleType
                    ?: unsupported("generic provision target is not a simple type: $binding")
                if (targetType.arguments.size != clazz.typeParameters.size) {
                    unsupported("raw generic provision targets are not supported: $binding")
                }
                targetType.arguments.map { argument ->
                    (argument as? IrTypeProjection)?.type
                        ?: unsupported("star-projected provision targets are not supported: $binding")
                }
            }
            val substitution = clazz.typeParameters.map { it.symbol }.zip(typeArguments).toMap()
            builder.irCallConstructor(platformModel, typeArguments).apply {
                fillArguments(this, builder, owner, constructor.regularParameters(), inputs, thisExpr, substitution)
            }
        }

        is IrSimpleFunctionSymbol -> {
            val function = platformModel.owner
            if (function.typeParameters.isNotEmpty()) {
                unsupported("generic provision functions are not supported: $binding")
            }
            val call = builder.irCall(platformModel).apply {
                if (function.parameters.any { it.kind == IrParameterKind.DispatchReceiver }) {
                    dispatchReceiver = provisionDispatchReceiver(builder, owner, binding, function, thisExpr)
                }
                fillArguments(this, builder, owner, function.regularParameters(), inputs, thisExpr, emptyMap())
            }
            if (options.enableProvisionNullChecks) {
                builder.irCall(symbols.checkProvisionNotNull, irBuiltIns.anyType).apply {
                    typeArguments[0] = irBuiltIns.anyType
                    arguments[regularArgumentOffset(symbols.checkProvisionNotNull.owner)] = call
                }
            } else {
                call
            }
        }

        else -> unsupported("provision callable is not backed by an IR symbol: $binding")
    }
}

private fun ComponentTreeEmitter.provisionDispatchReceiver(
    builder: IrBuilderWithScope,
    owner: GraphState,
    binding: ProvisionBinding,
    function: IrSimpleFunction,
    thisExpr: () -> IrExpression,
): IrExpression {
    if (binding.requiresModuleInstance) {
        val module = binding.originModule
            ?: unsupported("module instance provision without a module: $binding")
        val ownerState = states.getValue(binding.owner)
        val component = componentExpression(builder, owner, thisExpr, ownerState)
        return builder.irGetField(component, ownerState.moduleFields.getValue(module))
    }
    // Kotlin `object` modules and (`@JvmStatic`/inherited) companion members dispatch on
    // the object instance. The IR function's parent covers members declared in the object;
    // for members inherited from a base class, the lang model's owner is the object.
    val parentClass = function.parent as? IrClass
    if (parentClass?.isObject == true) {
        return builder.irGetObject(parentClass.symbol)
    }
    val langOwner = (binding.provision as? Method)?.owner?.platformModel as? IrClassSymbol
    if (langOwner?.owner?.isObject == true) {
        return builder.irGetObject(langOwner)
    }
    unsupported("non-static provision `${binding.provision}` without a module instance: $binding")
}

private fun ComponentTreeEmitter.fillArguments(
    call: IrFunctionAccessExpression,
    builder: IrBuilderWithScope,
    owner: GraphState,
    parameters: List<IrValueParameter>,
    inputs: List<NodeDependency>,
    thisExpr: () -> IrExpression,
    substitution: Map<IrTypeParameterSymbol, IrType>,
) {
    if (parameters.size != inputs.size) {
        unsupported("provision parameter count mismatch: expected ${parameters.size}, got ${inputs.size}")
    }
    val allParameters = call.symbol.owner.parameters
    parameters.forEachIndexed { index, parameter ->
        call.arguments[allParameters.indexOf(parameter)] = access(
            builder = builder,
            inside = owner,
            thisExpr = thisExpr,
            dependency = inputs[index],
            expectedType = parameter.type.substituteTypeParameters(substitution),
        )
    }
}

private fun ComponentTreeEmitter.alternativesExpression(
    builder: IrBuilderWithScope,
    owner: GraphState,
    binding: AlternativesBinding,
    thisExpr: () -> IrExpression,
): IrExpression {
    var scopeOfPrevious: ConditionScope = ConditionScope.Never
    val entries = mutableListOf<Pair<Binding, ConditionScope.ExpressionScope?>>()
    for ((index, alternative) in binding.alternatives.withIndex()) {
        val alternativeBinding = binding.owner.resolveBinding(alternative)
        val scope = alternativeBinding.conditionScope
        val reachableScope = !scopeOfPrevious and scope
        scopeOfPrevious = scopeOfPrevious or scope
        val isLast = index == binding.alternatives.size - 1
        if ((isLast && scope != ConditionScope.Never) || scope == ConditionScope.Always) {
            entries += alternativeBinding to null
            break
        }
        if (scope == ConditionScope.Never || reachableScope.isContradiction()) continue
        entries += alternativeBinding to (scope as ConditionScope.ExpressionScope)
    }
    if (entries.isEmpty()) {
        unsupported("no reachable alternatives: $binding")
    }
    fun accessOf(target: Binding): IrExpression =
        accessKind(builder, owner, thisExpr, target, DependencyKind.Direct)
    // The final else duplicates the last entry's access when it is conditional (reference quirk).
    var result: IrExpression = accessOf(entries.last().first)
    val conditionals = if (entries.last().second == null) entries.dropLast(1) else entries
    for ((alternativeBinding, scope) in conditionals.asReversed()) {
        result = builder.irIfThenElse(
            irBuiltIns.anyNType,
            conditionExpression(builder, owner, thisExpr, scope!!),
            accessOf(alternativeBinding),
            result,
        )
    }
    return result
}
