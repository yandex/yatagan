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
import com.yandex.yatagan.core.graph.ThreadChecker
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
import com.yandex.yatagan.core.model.BooleanExpression
import com.yandex.yatagan.core.model.CollectionTargetKind
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.lang.Annotation as LangAnnotation
import com.yandex.yatagan.lang.Field as LangField
import com.yandex.yatagan.lang.Member as LangMember
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.irChar
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irShort
import org.jetbrains.kotlin.ir.builders.irString
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
import org.jetbrains.kotlin.ir.builders.irFalse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Emits the compiled implementation hierarchy for an already resolved root [BindingGraph].
 *
 * Supported: explicit component creators (builder-setter and factory-method styles) with
 * `@BindsInstance` and module-instance inputs, subcomponents (child-builder entry points and
 * parent-declared factory methods), scoped caching (single- and multi-thread), aliases,
 * List/Set multibindings, `Lazy`/`Provider` wrappers and unconditional `Optional` dependencies.
 *
 * [prepare] completes the full support check and builds all IR off-tree without attaching it.
 */
public class IrComponentEmitter(
    private val pluginContext: IrPluginContext,
    private val targetFile: IrFile,
    private val graph: BindingGraph,
    private val options: Options = Options(),
) {
    public class Options(
        public val enableProvisionNullChecks: Boolean = true,
        public val threadChecker: ThreadChecker? = null,
    )

    /**
     * Checks the complete graph and returns a prepared emission that can be executed later.
     *
     * @throws UnsupportedIrGraphException if any part of the graph is outside the supported subset.
     */
    public fun prepare(): PreparedIrComponent {
        val emitter = ComponentTreeEmitter(pluginContext, targetFile, graph, options)
        val implementation = emitter.buildOffTree()
        return PreparedIrComponent {
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

        /** Attaches the implementation after successful preparation. */
        public fun emit(): IrClass = implementation
    }
}

/** Raised when [IrComponentEmitter] is asked to emit semantics outside its supported subset. */
public class UnsupportedIrGraphException(
    message: String,
) : IllegalArgumentException(message)

private fun unsupported(reason: String): Nothing = throw UnsupportedIrGraphException(reason)

private val YataganIrOrigin = IrDeclarationOriginImpl("YATAGAN_GRAPH_IR")

private fun IrFunction.regularParameters(): List<IrValueParameter> =
    parameters.filter { it.kind == IrParameterKind.Regular }

private class RuntimeSymbols(
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

private class AssistedFactoryImplState(
    val constructor: IrConstructor,
    val delegateField: IrField,
    val clazz: IrClass,
    /** Maps the factory interface's type parameters to the concrete use-site arguments. */
    val substitution: Map<IrTypeParameterSymbol, IrType>,
)

private class ProviderClass(
    val constructor: IrConstructor,
    val delegateField: IrField,
    val indexField: IrField,
    val valueField: IrField?,
    val getFunction: IrSimpleFunction,
    val getSlowFunction: IrSimpleFunction?,
)

private class GraphState(
    val graph: BindingGraph,
    val implClass: IrClass,
) {
    lateinit var constructor: IrConstructor
    val usedParentsOrdered: List<BindingGraph> = graph.usedParents.toList()
    val parentFields = LinkedHashMap<BindingGraph, IrField>()
    val instanceFields = LinkedHashMap<NodeModel, IrField>()
    val moduleFields = LinkedHashMap<ModuleModel, IrField>()
    val dependencyFields = LinkedHashMap<ComponentDependencyModel, IrField>()

    val accessors = HashMap<Binding, IrSimpleFunction>()
    val cacheFields = HashMap<Binding, IrField>()
    val cacheSlowMethods = HashMap<Binding, IrSimpleFunction>()
    val optionalAccessors = HashMap<Pair<Binding, DependencyKind>, IrSimpleFunction>()
    val slots = LinkedHashMap<Binding, Int>()

    var switchFunction: IrSimpleFunction? = null
    var providerImpl: ProviderClass? = null
    var cachingProviderImpl: ProviderClass? = null

    val assistedFactoryImpls = LinkedHashMap<AssistedInjectFactoryModel, AssistedFactoryImplState>()
    val eagerLiteralFields = LinkedHashMap<ConditionModel, IrField>()
    val lazyLiteralFields = LinkedHashMap<ConditionModel, IrField>()
    val lazyLiteralAccessors = LinkedHashMap<ConditionModel, IrSimpleFunction>()

    var factoryImpl: IrClass? = null
    lateinit var factoryConstructor: IrConstructor
    val factoryParentFields = LinkedHashMap<BindingGraph, IrField>()
    val factoryBuilderFields = LinkedHashMap<ComponentFactoryModel.InputModel, IrField>()

    val creatorInputsOrdered: List<ComponentFactoryModel.InputModel> = graph.creator?.let { creator ->
        creator.factoryInputs.toList() +
                ((creator as? ComponentFactoryWithBuilderModel)?.builderInputs?.toList().orEmpty())
    } ?: emptyList()

    fun slotFor(binding: Binding): Int = slots.getValue(binding)
}

private class ComponentTreeEmitter(
    private val pluginContext: IrPluginContext,
    private val targetFile: IrFile,
    private val rootGraph: BindingGraph,
    private val options: IrComponentEmitter.Options,
) {
    private val symbols = RuntimeSymbols(pluginContext, pluginContext.finderForSource(targetFile))
    private val irBuiltIns = pluginContext.irBuiltIns
    private val states = LinkedHashMap<BindingGraph, GraphState>()

    private val anyConstructor by lazy { irBuiltIns.anyClass.owner.constructors.single() }

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

    // region Preflight

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

    // endregion

    // region Shells: classes, fields, constructors

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

    private fun buildAssistedFactoryShells(state: GraphState) {
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

    private fun parentParams(state: GraphState, parent: BindingGraph): Int =
        state.usedParentsOrdered.indexOf(parent)

    private fun ComponentFactoryModel.InputModel.irInputType(): IrType =
        when (val payload = payload) {
            is ComponentFactoryModel.InputPayload.Instance -> payload.model.type.toIrType()
            is ComponentFactoryModel.InputPayload.Module -> payload.model.moduleClassSymbol().defaultType
            is ComponentFactoryModel.InputPayload.Dependency -> payload.model.dependencyClassSymbol().defaultType
        }

    private fun buildFactoryShell(state: GraphState) {
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

    // endregion

    // region Member declarations

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

    private fun buildProviderClass(state: GraphState, caching: Boolean): ProviderClass {
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

    // endregion

    // region Bodies

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

    private fun emitSwitchAndProviders(state: GraphState) {
        state.switchFunction?.let { emitSwitch(state, it) }
        state.providerImpl?.let { emitProviderBodies(state, it, caching = false) }
        state.cachingProviderImpl?.let { emitProviderBodies(state, it, caching = true) }
    }

    /**
     * Creates an override of [overridden] in this class, handling both plain methods and
     * property accessors (which require an [org.jetbrains.kotlin.ir.declarations.IrProperty] wrapper).
     */
    private fun IrClass.addOverride(
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

    private fun emitAssistedFactoryBodies(state: GraphState) {
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

    private fun emitFactoryBodies(state: GraphState) {
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

    private fun emitStaticEntries(state: GraphState) {
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
    private fun buildAutoBuilderImpl(state: GraphState): IrConstructor {
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

    private fun emitCachePair(state: GraphState, binding: Binding) {
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

    private fun emitOptionalAccessor(
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

    private fun emitSwitch(state: GraphState, switchFn: IrSimpleFunction) {
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

    private fun emitProviderBodies(state: GraphState, provider: ProviderClass, caching: Boolean) {
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

    // endregion

    // region Access & creation expressions

    private fun access(
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

    private fun accessKind(
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

    private fun IrBuilderWithScope.newProvider(
        provider: ProviderClass,
        component: IrExpression,
        slot: Int,
    ): IrExpression = irCallConstructor(provider.constructor.symbol, emptyList()).apply {
        arguments[0] = component
        arguments[1] = irInt(slot)
    }

    private fun directAccessOn(
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

    private fun directAccess(
        builder: IrBuilderWithScope,
        inside: GraphState,
        thisExpr: () -> IrExpression,
        binding: Binding,
    ): IrExpression {
        val owner = states.getValue(binding.owner)
        return directAccessOn(builder, owner, { componentExpression(builder, inside, thisExpr, owner) }, binding)
    }

    private fun componentExpression(
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
    private fun creationExpression(
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

    private fun provisionExpression(
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
                    fillArguments(builder, owner, constructor.regularParameters(), inputs, thisExpr, substitution)
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
                    fillArguments(builder, owner, function.regularParameters(), inputs, thisExpr, emptyMap())
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

    private fun provisionDispatchReceiver(
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

    private fun IrFunctionAccessExpression.fillArguments(
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
        val allParameters = this.symbol.owner.parameters
        parameters.forEachIndexed { index, parameter ->
            arguments[allParameters.indexOf(parameter)] = access(
                builder = builder,
                inside = owner,
                thisExpr = thisExpr,
                dependency = inputs[index],
                expectedType = parameter.type.substituteTypeParameters(substitution),
            )
        }
    }

    private fun multiBindingExpression(
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

    private fun alternativesExpression(
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

    private fun mapBindingExpression(
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

    private fun annotationValueLiteral(
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

    private fun literalEvaluation(
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

    private fun literalAccess(
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

    private fun conditionExpression(
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

    private fun irJavaClassLiteral(builder: IrBuilderWithScope, classSymbol: IrClassSymbol): IrExpression {
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

    // endregion

    // region Helpers

    private fun threadAssertionCall(builder: IrBuilderWithScope, state: GraphState): IrExpression? {
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

    private fun builderFor(symbol: IrSymbol): DeclarationIrBuilder =
        DeclarationIrBuilder(pluginContext, symbol)

    private fun annotationCall(constructor: IrConstructorSymbol): IrAnnotation =
        DeclarationIrBuilder(pluginContext, constructor).irAnnotation(constructor)

    private fun BindingGraph.componentClassSymbol(): IrClassSymbol =
        model.type.declaration.platformModel as? IrClassSymbol
            ?: unsupported("component type is not backed by an IrClassSymbol: ${model.type}")

    private fun ComponentDependencyModel.dependencyClassSymbol(): IrClassSymbol =
        type.declaration.platformModel as? IrClassSymbol
            ?: unsupported("component dependency type is not backed by an IrClassSymbol: $type")

    private fun ModuleModel.moduleClassSymbol(): IrClassSymbol =
        type.declaration.platformModel as? IrClassSymbol
            ?: unsupported("module type is not backed by an IrClassSymbol: $type")

    private fun Type.toIrType(): IrType {
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

    private fun IrType.substituteTypeParameters(substitution: Map<IrTypeParameterSymbol, IrType>): IrType {
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

    private fun rootImplementationName(component: IrClass): Name {
        val nesting = generateSequence(component) { declaration -> declaration.parent as? IrClass }
            .toList()
            .asReversed()
        return Name.identifier("Yatagan" + nesting.joinToString("_") { it.name.asString() })
    }

    private fun childImplementationName(parentImpl: IrClass, component: IrClass): Name {
        val base = component.name.asString() + "Impl"
        var candidate = base
        var counter = 0
        while (parentImpl.declarations.filterIsInstance<IrClass>().any { it.name.asString() == candidate }) {
            candidate = base + counter++
        }
        return Name.identifier(candidate)
    }

    private fun sanitizeName(name: String): String =
        name.filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { "arg" }

    private fun regularArgumentOffset(function: IrFunction): Int =
        function.parameters.indexOfFirst { it.kind == IrParameterKind.Regular }.also { check(it >= 0) }

    // endregion
}
