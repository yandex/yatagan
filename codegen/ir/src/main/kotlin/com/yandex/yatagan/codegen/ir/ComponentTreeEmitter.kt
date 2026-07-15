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
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.ScopeModel
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name

internal class ComponentTreeEmitter(
    internal val pluginContext: IrPluginContext,
    private val targetFile: IrFile,
    private val rootGraph: BindingGraph,
    internal val options: IrComponentEmitter.Options,
) {
    internal val symbols = RuntimeSymbols(pluginContext, pluginContext.finderForSource(targetFile))
    internal val irBuiltIns = pluginContext.irBuiltIns
    internal val states = LinkedHashMap<BindingGraph, GraphState>()

    internal val anyConstructor by lazy { irBuiltIns.anyClass.owner.constructors.single() }

    fun buildOffTree(): IrClass {
        preflight(rootGraph)
        val rootImpl = buildShells(rootGraph, parentImpl = null).implClass
        states.values.forEach(::declareMembers)
        states.values.forEach(::emitBodies)
        // Switch and provider bodies are emitted last: their contents are driven by the slot
        // tables which must be complete by now.
        states.values.forEach(::emitSwitchAndProviders)
        return rootImpl
    }

    private fun preflight(graph: BindingGraph) {
        if (graph === rootGraph) {
            val componentClass = graph.componentClassSymbol().owner
            val implementationName = rootImplementationName(componentClass)
            if (targetFile.declarations.asSequence().filterIsInstance<IrClass>()
                    .any { it.name == implementationName }) {
                unsupported("implementation ${implementationName.asString()} already exists")
            }
        }
        val componentClass = graph.componentClassSymbol().owner
        if (componentClass.kind != ClassKind.INTERFACE) {
            unsupported("only interface components are supported")
        }
        if (componentClass.typeParameters.isNotEmpty()) {
            unsupported("generic components are not supported")
        }
        for (binding in graph.localBindings.keys) {
            preflightBinding(binding)
        }
        graph.children.forEach(::preflight)
    }

    private fun preflightBinding(binding: Binding) {
        when (binding) {
            is ProvisionBinding, is InstanceBinding, is ComponentInstanceBinding,
            is SubComponentFactoryBinding, is EmptyBinding, is MultiBinding,
            is ComponentDependencyBinding, is ComponentDependencyEntryPointBinding,
            is MapBinding,
                -> Unit

            is AssistedInjectFactoryBinding -> {
                val model = binding.model
                val factoryMethod = model.factoryMethod
                    ?: unsupported("assisted factory has no factory method: $binding")
                if (factoryMethod.platformModel !is IrSimpleFunctionSymbol) {
                    unsupported("assisted factory method is not backed by an IrSimpleFunctionSymbol: $binding")
                }
                val constructor = model.assistedInjectConstructor
                    ?: unsupported("assisted factory has no @AssistedInject constructor: $binding")
                if (constructor.platformModel !is IrConstructorSymbol) {
                    unsupported("assisted-inject constructor is not backed by an IrConstructorSymbol: $binding")
                }
            }

            is AlternativesBinding, is ConditionExpressionValueBinding -> Unit

            else -> unsupported("unsupported binding kind ${binding.javaClass.simpleName}: $binding")
        }
    }

    private fun buildShells(graph: BindingGraph, parentImpl: IrClass?): GraphState {
        val componentClass = graph.componentClassSymbol().owner
        val implName = if (parentImpl == null) {
            rootImplementationName(componentClass)
        } else {
            childImplementationName(parentImpl, componentClass)
        }
        val implClass = pluginContext.irFactory.buildClass {
            startOffset = componentClass.startOffset
            endOffset = componentClass.endOffset
            origin = YataganIrOrigin
            name = implName
            kind = ClassKind.CLASS
            modality = Modality.FINAL
            visibility = DescriptorVisibilities.PUBLIC
        }.apply {
            createThisReceiverParameter()
            superTypes += componentClass.symbol.defaultType
        }
        parentImpl?.addChild(implClass)
        symbols.yataganGeneratedConstructor?.let { generatedConstructor ->
            implClass.annotations += annotationCall(generatedConstructor)
        }

        val state = GraphState(graph, implClass)
        states[graph] = state

        for (parent in state.usedParentsOrdered) {
            val parentState = states[parent] ?: error("parent graph state must be built first")
            state.parentFields[parent] = implClass.addField {
                name = Name.identifier("p${state.parentFields.size}")
                type = parentState.implClass.defaultType
                visibility = DescriptorVisibilities.PUBLIC
                isFinal = true
                origin = YataganIrOrigin
            }
        }
        for (input in state.creatorInputsOrdered) {
            val payload = input.payload
            if (payload !is ComponentFactoryModel.InputPayload.Instance) continue
            state.instanceFields.getOrPut(payload.model) {
                implClass.addField {
                    name = Name.identifier("i${state.instanceFields.size}")
                    type = payload.model.type.toIrType().makeNullable()
                    visibility = DescriptorVisibilities.PUBLIC
                    isFinal = true
                    origin = YataganIrOrigin
                }
            }
        }
        for (dependency in graph.dependencies) {
            state.dependencyFields[dependency] = implClass.addField {
                name = Name.identifier("d${state.dependencyFields.size}")
                type = dependency.dependencyClassSymbol().defaultType
                visibility = DescriptorVisibilities.PUBLIC
                isFinal = true
                origin = YataganIrOrigin
            }
        }
        for (module in graph.modules) {
            if (!module.requiresInstance) continue
            state.moduleFields[module] = implClass.addField {
                name = Name.identifier("m${state.moduleFields.size}")
                type = module.moduleClassSymbol().defaultType
                visibility = DescriptorVisibilities.PUBLIC
                isFinal = true
                origin = YataganIrOrigin
            }
        }
        val multiThread = graph.requiresSynchronizedAccess
        for (binding in graph.localBindings.keys) {
            if (binding.scopes.isEmpty() || binding.conditionScope == ConditionScope.Never) continue
            val field = implClass.addField {
                name = Name.identifier("c${state.cacheFields.size}")
                type = irBuiltIns.anyNType
                visibility = DescriptorVisibilities.PRIVATE
                isFinal = false
                origin = YataganIrOrigin
            }
            if (multiThread && ScopeModel.Reusable !in binding.scopes) {
                field.annotations += annotationCall(symbols.volatileAnnotationConstructor)
            }
            state.cacheFields[binding] = field
        }

        for ((literal, usage) in graph.localConditionLiterals) {
            when (usage) {
                BindingGraph.LiteralUsage.Eager -> state.eagerLiteralFields[literal] = implClass.addField {
                    name = Name.identifier("le${state.eagerLiteralFields.size}")
                    type = irBuiltIns.booleanType
                    visibility = DescriptorVisibilities.PUBLIC
                    isFinal = true
                    origin = YataganIrOrigin
                }

                BindingGraph.LiteralUsage.Lazy -> state.lazyLiteralFields[literal] = implClass.addField {
                    name = Name.identifier("ll${state.lazyLiteralFields.size}")
                    type = irBuiltIns.intType
                    visibility = DescriptorVisibilities.PRIVATE
                    isFinal = false
                    origin = YataganIrOrigin
                }
            }
        }

        buildConstructor(state)
        buildFactoryShell(state)
        buildAssistedFactoryShells(state)

        graph.children.forEach { child -> buildShells(child, implClass) }
        return state
    }

    private fun buildConstructor(state: GraphState) {
        state.constructor = state.implClass.addConstructor {
            visibility = DescriptorVisibilities.PUBLIC
            isPrimary = true
            origin = YataganIrOrigin
        }.apply {
            val parentParams = state.usedParentsOrdered.map { parent ->
                addValueParameter("p${parentParams(state, parent)}", states.getValue(parent).implClass.defaultType)
            }
            val inputParams = state.creatorInputsOrdered.map { input ->
                addValueParameter(sanitizeName(input.name), input.irInputType().makeNullable())
            }
            val autoInputParams: Map<Any, IrValueParameter> = if (state.graph.creator == null) {
                buildMap {
                    state.dependencyFields.forEach { (dependency, field) ->
                        put(dependency, addValueParameter("a${size}", field.type.makeNullable()))
                    }
                    state.moduleFields.forEach { (module, field) ->
                        put(module, addValueParameter("a${size}", field.type.makeNullable()))
                    }
                }
            } else emptyMap()
            body = builderFor(symbol).irBlockBody {
                +irDelegatingConstructorCall(anyConstructor)
                +IrInstanceInitializerCallImpl(
                    startOffset, endOffset, state.implClass.symbol, irBuiltIns.unitType,
                )
                fun thisExpr() = irGet(state.implClass.thisReceiver!!)
                state.usedParentsOrdered.forEachIndexed { index, parent ->
                    +irSetField(thisExpr(), state.parentFields.getValue(parent), irGet(parentParams[index]))
                }
                val suppliedModules = mutableSetOf<ModuleModel>()
                state.creatorInputsOrdered.forEachIndexed { index, input ->
                    val param = inputParams[index]
                    val value: IrExpression = if (options.enableProvisionNullChecks) {
                        irCall(symbols.checkInputNotNull, irBuiltIns.anyType).apply {
                            typeArguments[0] = irBuiltIns.anyType
                            arguments[regularArgumentOffset(symbols.checkInputNotNull.owner)] = irGet(param)
                        }
                    } else {
                        irGet(param)
                    }
                    val field = when (val payload = input.payload) {
                        is ComponentFactoryModel.InputPayload.Instance ->
                            state.instanceFields.getValue(payload.model)

                        is ComponentFactoryModel.InputPayload.Module -> {
                            suppliedModules += payload.model
                            state.moduleFields.getValue(payload.model)
                        }

                        is ComponentFactoryModel.InputPayload.Dependency ->
                            state.dependencyFields.getValue(payload.model)
                    }
                    +irSetField(thisExpr(), field, irAs(value, field.type))
                }
                state.dependencyFields.forEach { (dependency, field) ->
                    autoInputParams[dependency]?.let { param ->
                        +irSetField(thisExpr(), field, irAs(irGet(param), field.type))
                    }
                }
                for ((module, field) in state.moduleFields) {
                    if (module in suppliedModules) continue
                    val moduleConstructor = if (module.isTriviallyConstructable) {
                        module.moduleClassSymbol().owner.constructors
                            .firstOrNull { it.regularParameters().isEmpty() }
                            ?: unsupported("module ${module.type} has no no-arg constructor")
                    } else null
                    val param = autoInputParams[module]
                    val moduleValue: IrExpression = when {
                        param != null && moduleConstructor != null ->
                            // Optional auto-builder input: null-coalesce to a fresh instance.
                            irBlock(resultType = field.type) {
                                val local = irTemporary(irGet(param))
                                +irIfThenElse(
                                    field.type,
                                    irEqualsNull(irGet(local)),
                                    irCallConstructor(moduleConstructor.symbol, emptyList()),
                                    irAs(irGet(local), field.type),
                                )
                            }

                        param != null -> irAs(irGet(param), field.type) // checked in AutoBuilderImpl.create()
                        moduleConstructor != null -> irCallConstructor(moduleConstructor.symbol, emptyList())
                        else -> unsupported("module ${module.type} requires an instance " +
                                "that is not passed via the component creator")
                    }
                    +irSetField(thisExpr(), field, moduleValue)
                }
                for ((literal, literalField) in state.eagerLiteralFields) {
                    +irSetField(thisExpr(), literalField,
                        literalEvaluation(this, state, { thisExpr() }, literal))
                }
            }
        }
    }

    private fun declareMembers(state: GraphState) {
        val graph = state.graph
        for ((binding, usage) in graph.localBindings) {
            val alive = binding.conditionScope != ConditionScope.Never
            if (alive && needsAccessor(binding)) {
                val scoped = binding.scopes.isNotEmpty()
                state.accessors[binding] = state.implClass.addFunction(
                    name = "${if (scoped) "cache" else "access"}${state.accessors.size}",
                    returnType = irBuiltIns.anyNType,
                    modality = Modality.FINAL,
                    visibility = DescriptorVisibilities.PUBLIC,
                    origin = YataganIrOrigin,
                )
                if (scoped) {
                    val slow = state.implClass.addFunction(
                        name = "cacheSlow${state.cacheSlowMethods.size}",
                        returnType = irBuiltIns.anyNType,
                        modality = Modality.FINAL,
                        visibility = DescriptorVisibilities.PRIVATE,
                        origin = YataganIrOrigin,
                    )
                    if (graph.requiresSynchronizedAccess && ScopeModel.Reusable !in binding.scopes) {
                        slow.annotations += annotationCall(symbols.synchronizedAnnotationConstructor)
                    }
                    state.cacheSlowMethods[binding] = slow
                }
            }
            if (alive && usage.lazy + usage.provider + usage.optionalLazy + usage.optionalProvider > 0) {
                state.slots.getOrPut(binding) { state.slots.size }
            }
            for ((kind, count) in listOf(
                DependencyKind.Optional to usage.optional,
                DependencyKind.OptionalLazy to usage.optionalLazy,
                DependencyKind.OptionalProvider to usage.optionalProvider,
            )) {
                if (count > 0) {
                    state.optionalAccessors[binding to kind] = state.implClass.addFunction(
                        name = "optOf${state.optionalAccessors.size}",
                        returnType = symbols.optionalClass.typeWith(irBuiltIns.anyType),
                        modality = Modality.FINAL,
                        visibility = DescriptorVisibilities.PUBLIC,
                        origin = YataganIrOrigin,
                    )
                }
            }
        }
        for ((literal, usage) in graph.localConditionLiterals) {
            if (usage != BindingGraph.LiteralUsage.Lazy) continue
            state.lazyLiteralAccessors[literal] = state.implClass.addFunction(
                name = "cond${state.lazyLiteralAccessors.size}",
                returnType = irBuiltIns.booleanType,
                modality = Modality.FINAL,
                visibility = DescriptorVisibilities.PUBLIC,
                origin = YataganIrOrigin,
            )
        }
        if (state.slots.isNotEmpty()) {
            state.switchFunction = state.implClass.addFunction(
                name = "switchAccess",
                returnType = irBuiltIns.anyNType,
                modality = Modality.FINAL,
                visibility = DescriptorVisibilities.PUBLIC,
                origin = YataganIrOrigin,
            ).apply {
                addValueParameter("slot", irBuiltIns.intType)
            }
            state.providerImpl = buildProviderClass(state, caching = false)
            val needsCachingProvider = graph.localBindings.any { (binding, usage) ->
                binding.scopes.isEmpty() && binding.conditionScope != ConditionScope.Never &&
                        usage.lazy + usage.optionalLazy > 0
            }
            if (needsCachingProvider) {
                state.cachingProviderImpl = buildProviderClass(state, caching = true)
            }
        }
    }

    private fun needsAccessor(binding: Binding): Boolean = when (binding) {
        is InstanceBinding, is ComponentInstanceBinding, is EmptyBinding,
        is ComponentDependencyBinding,
            -> false

        else -> true
    }

    private fun emitBodies(state: GraphState) {
        emitEntryPoints(state)
        emitMemberInjectors(state)
        emitSubComponentFactoryMethods(state)
        emitAssistedFactoryBodies(state)
        emitFactoryBodies(state)
        emitStaticEntries(state)

        for (binding in state.graph.localBindings.keys) {
            if (binding.conditionScope == ConditionScope.Never || !needsAccessor(binding)) continue
            if (binding.scopes.isNotEmpty()) {
                emitCachePair(state, binding)
            } else {
                val accessor = state.accessors.getValue(binding)
                accessor.body = builderFor(accessor.symbol).irBlockBody {
                    +irReturn(creationExpression(this, state, binding,
                        thisExpr = { irGet(accessor.dispatchReceiverParameter!!) }))
                }
            }
        }
        for ((key, accessor) in state.optionalAccessors) {
            val (binding, kind) = key
            emitOptionalAccessor(state, binding, kind, accessor)
        }
        emitLazyLiteralAccessors(state)
    }

    private fun emitSwitchAndProviders(state: GraphState) {
        state.switchFunction?.let { emitSwitch(state, it) }
        state.providerImpl?.let { emitProviderBodies(state, it, caching = false) }
        state.cachingProviderImpl?.let { emitProviderBodies(state, it, caching = true) }
    }

    private fun emitEntryPoints(state: GraphState) {
        for (entryPoint in state.graph.entryPoints) {
            val getter = entryPoint.getter
            val dependency = entryPoint.dependency
            val getterSymbol = getter.platformModel as? IrSimpleFunctionSymbol
                ?: unsupported("entry point $getter is not backed by an IrSimpleFunctionSymbol")
            if (getterSymbol.owner.regularParameters().isNotEmpty()) {
                unsupported("entry point $getter must have no parameters")
            }
            val override = state.implClass.addOverride(getterSymbol)
            override.body = builderFor(override.symbol).irBlockBody {
                +irReturn(access(
                    builder = this,
                    inside = state,
                    thisExpr = { irGet(override.dispatchReceiverParameter!!) },
                    dependency = dependency,
                    expectedType = override.returnType,
                ))
            }
        }
    }

    private fun emitMemberInjectors(state: GraphState) {
        for (memberInjector in state.graph.memberInjectors) {
            val methodSymbol = memberInjector.injector.platformModel as? IrSimpleFunctionSymbol
                ?: unsupported("member injector is not backed by an IrSimpleFunctionSymbol")
            val override = state.implClass.addOverride(methodSymbol)
            override.body = builderFor(override.symbol).irBlockBody {
                val target = override.regularParameters().single()
                for ((member, dependency) in memberInjector.membersToInject) {
                    val thisExpr = { irGet(override.dispatchReceiverParameter!!) }
                    when (val platformModel = member.platformModel) {
                        is IrFieldSymbol -> {
                            // Kotlin property backing fields are private at the IR level; a cross-file
                            // irSetField would require a synthetic accessor in the owner's file, which
                            // the JVM backend forbids. Go through the setter when there is one.
                            val setter = platformModel.owner.correspondingPropertySymbol?.owner?.setter
                            if (setter != null) {
                                +irCall(setter.symbol).apply {
                                    dispatchReceiver = irGet(target)
                                    val parameter = setter.regularParameters().single()
                                    arguments[setter.parameters.indexOf(parameter)] =
                                        access(this@irBlockBody, state, thisExpr, dependency, parameter.type)
                                }
                            } else {
                                +irSetField(
                                    irGet(target),
                                    platformModel.owner,
                                    access(this, state, thisExpr, dependency, platformModel.owner.type),
                                )
                            }
                        }

                        is IrSimpleFunctionSymbol -> +irCall(platformModel).apply {
                            dispatchReceiver = irGet(target)
                            val parameter = platformModel.owner.regularParameters().single()
                            arguments[platformModel.owner.parameters.indexOf(parameter)] =
                                access(this@irBlockBody, state, thisExpr, dependency, parameter.type)
                        }

                        else -> unsupported("injectee member $member is not backed by an IR symbol")
                    }
                }
            }
        }
    }

    private fun emitSubComponentFactoryMethods(state: GraphState) {
        for (factoryMethod in state.graph.subComponentFactoryMethods) {
            val createdGraph = factoryMethod.createdGraph ?: continue
            val methodSymbol = factoryMethod.model.factoryMethod.platformModel as? IrSimpleFunctionSymbol
                ?: unsupported("subcomponent factory method is not backed by an IrSimpleFunctionSymbol")
            val childState = states.getValue(createdGraph)
            val override = state.implClass.addOverride(methodSymbol)
            override.body = builderFor(override.symbol).irBlockBody {
                +irReturn(irCallConstructor(childState.constructor.symbol, emptyList()).apply {
                    var index = 0
                    childState.usedParentsOrdered.forEach { parent ->
                        arguments[index++] = componentExpression(
                            this@irBlockBody, state,
                            { irGet(override.dispatchReceiverParameter!!) },
                            states.getValue(parent),
                        )
                    }
                    override.regularParameters().forEach { parameter ->
                        arguments[index++] = irGet(parameter)
                    }
                })
            }
        }
    }
}
