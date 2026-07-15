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

@file:OptIn(
    com.yandex.yatagan.base.api.Incubating::class,
    org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class,
)

package com.yandex.yatagan.codegen.ir

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.GraphEntryPoint
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.TypeDeclarationKind
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.addSimpleDelegatingConstructor
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.name.Name

/**
 * Emits the minimal compiled implementation for an already resolved root [BindingGraph].
 *
 * This proof-of-concept intentionally accepts only direct, unscoped [ProvisionBinding]s backed by Kotlin IR
 * constructors or functions. [prepare] completes the full support check without creating or attaching any IR.
 */
public class IrComponentEmitter(
    private val pluginContext: IrPluginContext,
    private val targetFile: IrFile,
    private val graph: BindingGraph,
) {
    /**
     * Checks the complete graph and returns a prepared emission that can be executed later.
     *
     * This phase neither creates nor attaches IR, allowing callers to prepare every root before mutating the module.
     *
     * @throws UnsupportedIrGraphException if any part of the graph is outside this proof-of-concept's subset.
     */
    public fun prepare(): PreparedIrComponent {
        val plan = preflight()
        return PreparedIrComponent {
            val implementation = emitOffTree(plan)
            targetFile.addChild(implementation)
            implementation
        }
    }

    /** Adds the loader-named implementation to [targetFile]. */
    public fun emit(): IrClass = prepare().emit()

    public class PreparedIrComponent internal constructor(
        emission: () -> IrClass,
    ) {
        private val implementation: IrClass by lazy(LazyThreadSafetyMode.NONE, emission)

        /** Creates and attaches the implementation after successful preparation. */
        public fun emit(): IrClass = implementation
    }

    private fun preflight(): ComponentPlan {
        if (!graph.isRoot || graph.parent != null) {
            unsupported("only a root binding graph is supported")
        }
        if (graph.children.isNotEmpty()) {
            unsupported("subcomponent graphs are not supported")
        }
        if (graph.creator != null) {
            unsupported("explicit component creators are not supported")
        }
        if (graph.dependencies.isNotEmpty()) {
            unsupported("component dependencies are not supported")
        }
        if (graph.memberInjectors.isNotEmpty()) {
            unsupported("member injectors are not supported")
        }
        if (graph.subComponentFactoryMethods.isNotEmpty()) {
            unsupported("subcomponent factory methods are not supported")
        }
        if (graph.localConditionLiterals.isNotEmpty()) {
            unsupported("runtime conditions are not supported")
        }
        if (graph.localAssistedInjectFactories.isNotEmpty()) {
            unsupported("assisted injection is not supported")
        }

        val component = graph.model.type.declaration.platformModel as? IrClassSymbol
            ?: unsupported("component type is not backed by an IrClassSymbol")
        val componentClass = component.owner
        if (componentClass.kind != ClassKind.INTERFACE) {
            unsupported("only interface components are supported")
        }
        if (componentClass.typeParameters.isNotEmpty()) {
            unsupported("generic components are not supported")
        }

        val implementationName = implementationName(componentClass)
        if (targetFile.declarations.asSequence()
                .filterIsInstance<IrClass>()
                .any { declaration -> declaration.name == implementationName }) {
            unsupported("implementation ${implementationName.asString()} already exists")
        }

        val planner = ProvisionPlanner()
        graph.localBindings.keys.forEach(planner::plan)
        val entryPoints = graph.entryPoints.map { entryPoint ->
            planEntryPoint(entryPoint, planner)
        }
        return ComponentPlan(
            component = component,
            implementationName = implementationName,
            entryPoints = entryPoints,
        )
    }

    private fun planEntryPoint(
        entryPoint: GraphEntryPoint,
        planner: ProvisionPlanner,
    ): EntryPointPlan {
        if (entryPoint.dependency.kind != DependencyKind.Direct) {
            unsupported("entry point ${entryPoint.getter.name} is not a direct dependency")
        }
        val getter = entryPoint.getter.platformModel as? IrSimpleFunctionSymbol
            ?: unsupported("entry point ${entryPoint.getter.name} is not backed by an IrSimpleFunctionSymbol")
        val function = getter.owner
        if (function.name.isSpecial) {
            unsupported("special entry point ${function.name} is not supported")
        }
        if (function.parameters.any { parameter ->
                parameter.kind != IrParameterKind.DispatchReceiver }) {
            unsupported("entry point ${entryPoint.getter.name} must have no inputs")
        }
        return EntryPointPlan(
            getter = getter,
            provision = planner.planDependency(entryPoint.dependency),
        )
    }

    private inner class ProvisionPlanner {
        private val plans = mutableMapOf<Binding, ProvisionPlan>()
        private val active = mutableSetOf<Binding>()

        fun planDependency(dependency: NodeDependency): ProvisionPlan {
            if (dependency.kind != DependencyKind.Direct) {
                unsupported("only direct provision dependencies are supported: $dependency")
            }
            return plan(graph.resolveBinding(dependency.node))
        }

        fun plan(binding: Binding): ProvisionPlan {
            plans[binding]?.let { return it }
            if (!active.add(binding)) {
                unsupported("cyclic provision dependency: $binding")
            }
            try {
                if (binding !is ProvisionBinding) {
                    unsupported(
                        "unsupported binding kind ${binding.javaClass.simpleName}; " +
                                "only ProvisionBinding is supported",
                    )
                }
                if (binding.owner !== graph) {
                    unsupported("provisions owned by another graph are not supported: $binding")
                }
                if (binding.conditionScope != ConditionScope.Always ||
                    binding.dependenciesOnConditions.isNotEmpty() ||
                    binding.nonStaticConditionProviders.isNotEmpty()) {
                    unsupported("conditional provisions are not supported: $binding")
                }
                if (binding.scopes.isNotEmpty()) {
                    unsupported("scoped provisions are not supported: $binding")
                }
                if (binding.requiresModuleInstance) {
                    unsupported("module instance provisions are not supported: $binding")
                }

                val inputs = binding.inputs.map(::planDependency)
                val callable = binding.provision
                val platformModel = callable.platformModel
                val plan = when (platformModel) {
                    is IrConstructorSymbol -> planConstructor(platformModel, inputs, binding)
                    is IrSimpleFunctionSymbol -> planFunction(platformModel, inputs, binding)
                    else -> unsupported(
                        "provision callable is not backed by an IrConstructorSymbol or " +
                                "IrSimpleFunctionSymbol: $binding",
                    )
                }
                plans[binding] = plan
                return plan
            } finally {
                active.remove(binding)
            }
        }

        private fun planConstructor(
            symbol: IrConstructorSymbol,
            inputs: List<ProvisionPlan>,
            binding: ProvisionBinding,
        ): ProvisionPlan {
            val constructor = symbol.owner
            if ((constructor.parent as IrClass).typeParameters.isNotEmpty()) {
                unsupported("generic constructor provisions are not supported: $binding")
            }
            val argumentIndices = regularArgumentIndices(constructor.parameters, binding)
            if (argumentIndices.size != inputs.size) {
                unsupported("constructor input count does not match the binding graph: $binding")
            }
            return ConstructorPlan(
                symbol = symbol,
                arguments = argumentIndices.zip(inputs, ::CallArgument),
            )
        }

        private fun planFunction(
            symbol: IrSimpleFunctionSymbol,
            inputs: List<ProvisionPlan>,
            binding: ProvisionBinding,
        ): ProvisionPlan {
            val function = symbol.owner
            if (function.typeParameters.isNotEmpty()) {
                unsupported("generic provision functions are not supported: $binding")
            }
            val argumentIndices = regularArgumentIndices(function.parameters, binding)
            if (argumentIndices.size != inputs.size) {
                unsupported("function input count does not match the binding graph: $binding")
            }

            val dispatchObject = if (function.parameters.any { parameter ->
                    parameter.kind == IrParameterKind.DispatchReceiver }) {
                val method = binding.provision as? Method
                    ?: unsupported("function provision is not represented by a Method: $binding")
                val owner = method.owner.platformModel as? IrClassSymbol
                    ?: unsupported("provision method owner is not backed by an IrClassSymbol: $binding")
                if (method.owner.kind != TypeDeclarationKind.KotlinObject || !owner.owner.isObject) {
                    unsupported("only Kotlin object module dispatch is supported: $binding")
                }
                owner
            } else {
                null
            }

            return FunctionPlan(
                symbol = symbol,
                dispatchObject = dispatchObject,
                arguments = argumentIndices.zip(inputs, ::CallArgument),
            )
        }

        private fun regularArgumentIndices(
            parameters: List<org.jetbrains.kotlin.ir.declarations.IrValueParameter>,
            binding: ProvisionBinding,
        ): List<Int> {
            if (parameters.any { parameter ->
                    parameter.kind != IrParameterKind.DispatchReceiver &&
                            parameter.kind != IrParameterKind.Regular }) {
                unsupported("context and extension receivers are not supported: $binding")
            }
            return parameters.mapIndexedNotNull { index, parameter ->
                index.takeIf { parameter.kind == IrParameterKind.Regular }
            }
        }
    }

    private fun emitOffTree(plan: ComponentPlan): IrClass {
        val componentClass = plan.component.owner
        val implementation = pluginContext.irFactory.buildClass {
            startOffset = componentClass.startOffset
            endOffset = componentClass.endOffset
            origin = YataganIrOrigin
            name = plan.implementationName
            kind = ClassKind.CLASS
            modality = Modality.FINAL
            visibility = DescriptorVisibilities.PUBLIC
        }.apply {
            createThisReceiverParameter()
            superTypes += componentClass.symbol.defaultType
        }

        val constructor = implementation.addSimpleDelegatingConstructor(
            superConstructor = pluginContext.irBuiltIns.anyClass.owner.constructors.single(),
            irBuiltIns = pluginContext.irBuiltIns,
            isPrimary = true,
            origin = YataganIrOrigin,
        ).apply {
            visibility = DescriptorVisibilities.PRIVATE
        }

        plan.entryPoints.forEach { entryPoint ->
            implementation.addEntryPoint(entryPoint)
        }
        implementation.addFunction(
            name = "create",
            returnType = componentClass.symbol.defaultType,
            modality = Modality.FINAL,
            visibility = DescriptorVisibilities.PUBLIC,
            isStatic = true,
            origin = YataganIrOrigin,
            startOffset = componentClass.startOffset,
            endOffset = componentClass.endOffset,
        ).apply {
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                +irReturn(irCallConstructor(constructor.symbol, emptyList()))
            }
        }
        return implementation
    }

    private fun IrClass.addEntryPoint(plan: EntryPointPlan) {
        val getter = plan.getter.owner
        addFunction(
            name = getter.name.asString(),
            returnType = getter.returnType,
            modality = Modality.FINAL,
            visibility = DescriptorVisibilities.PUBLIC,
            origin = YataganIrOrigin,
            startOffset = getter.startOffset,
            endOffset = getter.endOffset,
        ).apply {
            overriddenSymbols = listOf(plan.getter)
            val builder = DeclarationIrBuilder(pluginContext, symbol)
            body = builder.irBlockBody {
                +irReturn(builder.irProvision(plan.provision))
            }
        }
    }

    private fun DeclarationIrBuilder.irProvision(plan: ProvisionPlan): IrExpression {
        return when (plan) {
            is ConstructorPlan -> irCallConstructor(
                callee = plan.symbol,
                typeArguments = emptyList(),
            ).apply {
                plan.arguments.forEach { argument ->
                    arguments[argument.parameterIndex] =
                        this@irProvision.irProvision(argument.provision)
                }
            }

            is FunctionPlan -> irCall(plan.symbol).apply {
                plan.dispatchObject?.let { dispatchObject ->
                    dispatchReceiver = irGetObject(dispatchObject)
                }
                plan.arguments.forEach { argument ->
                    arguments[argument.parameterIndex] =
                        this@irProvision.irProvision(argument.provision)
                }
            }
        }
    }

    private fun implementationName(component: IrClass): Name {
        val nesting = generateSequence(component) { declaration -> declaration.parent as? IrClass }
            .toList()
            .asReversed()
        return Name.identifier("Yatagan" + nesting.joinToString("_") { it.name.asString() })
    }

    private fun unsupported(reason: String): Nothing {
        throw UnsupportedIrGraphException(reason)
    }
}

/** Raised when [IrComponentEmitter] is asked to emit semantics outside its proof-of-concept subset. */
public class UnsupportedIrGraphException(
    message: String,
) : IllegalArgumentException(message)

private data class ComponentPlan(
    val component: IrClassSymbol,
    val implementationName: Name,
    val entryPoints: List<EntryPointPlan>,
)

private data class EntryPointPlan(
    val getter: IrSimpleFunctionSymbol,
    val provision: ProvisionPlan,
)

private sealed interface ProvisionPlan

private data class ConstructorPlan(
    val symbol: IrConstructorSymbol,
    val arguments: List<CallArgument>,
) : ProvisionPlan

private data class FunctionPlan(
    val symbol: IrSimpleFunctionSymbol,
    val dispatchObject: IrClassSymbol?,
    val arguments: List<CallArgument>,
) : ProvisionPlan

private data class CallArgument(
    val parameterIndex: Int,
    val provision: ProvisionPlan,
)

private val YataganIrOrigin = IrDeclarationOriginImpl("YATAGAN_GRAPH_IR")
