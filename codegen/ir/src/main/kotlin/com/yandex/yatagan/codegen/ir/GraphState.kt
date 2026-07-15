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

package com.yandex.yatagan.codegen.ir

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType

internal class GraphState(
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

internal class ProviderClass(
    val constructor: IrConstructor,
    val delegateField: IrField,
    val indexField: IrField,
    val valueField: IrField?,
    val getFunction: IrSimpleFunction,
    val getSlowFunction: IrSimpleFunction?,
)

internal class AssistedFactoryImplState(
    val constructor: IrConstructor,
    val delegateField: IrField,
    val clazz: IrClass,
    /** Maps the factory interface's type parameters to the concrete use-site arguments. */
    val substitution: Map<IrTypeParameterSymbol, IrType>,
)
