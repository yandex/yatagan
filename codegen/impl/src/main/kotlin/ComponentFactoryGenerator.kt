/*
 * Copyright 2022 Yandex LLC
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

package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeVariableName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.buildClass
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.codegen.poetry.invoke
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.Extensible
import com.yandex.yatagan.core.model.ClassBackedModel
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.allInputs
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

internal class ComponentFactoryGenerator(
    private val thisGraph: BindingGraph,
    private val componentImplName: ClassName,
    fieldsNs: Namespace,
) : ComponentGenerator.Contributor {
    private val inputsWithFieldNames = mutableMapOf<ClassBackedModel, String>()

    init {
        thisGraph.creator?.let { creator ->
            // Bound instances
            for (input in creator.allInputs) {
                val payload = input.payload as? InputPayload.Instance ?: continue
                inputsWithFieldNames[payload.model] = fieldsNs.name(input.name)
            }
        }

        // Component dependencies
        for (dependency in thisGraph.dependencies) {
            inputsWithFieldNames[dependency] = fieldsNs.name(dependency.name)
        }

        // Module instances
        for (module in thisGraph.modules) {
            if (module.requiresInstance) {
                inputsWithFieldNames[module] = fieldsNs.name(module.name)
            }
        }
    }

    private val superComponentFieldNames: Map<BindingGraph, String> =
        thisGraph.usedParents.associateWith { graph: BindingGraph ->
            fieldsNs.name(graph.model.name)
        }

    val implName: ClassName = componentImplName.run {
        // If no factory is present, use the component name itself (constructor).
        if (thisGraph.model.factory != null) nestedClass("ComponentFactoryImpl") else this
    }

    fun fieldNameFor(boundInstance: NodeModel) = checkNotNull(inputsWithFieldNames[boundInstance])
    fun fieldNameFor(dependency: ComponentDependencyModel) = checkNotNull(inputsWithFieldNames[dependency])
    fun fieldNameFor(module: ModuleModel) = checkNotNull(inputsWithFieldNames[module])
    fun fieldNameFor(graph: BindingGraph) = checkNotNull(superComponentFieldNames[graph])

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        inputsWithFieldNames.forEach { (inputModel, name) ->
            field(inputModel.typeName(), name) { modifiers(/*package-private*/ FINAL) }
        }
        superComponentFieldNames.forEach { (input, name) ->
            field(input[ComponentImplClassName], name) { modifiers(/*package-private*/ FINAL) }
        }
        generatePrimaryConstructor()

        thisGraph.model.factory?.let { factory ->
            generateBuilder(factory)
        }

        if (thisGraph.creator == null && thisGraph.isRoot) {
            generateAutoBuilder()
        }
    }

    private fun TypeSpecBuilder.generatePrimaryConstructor() {
        // Constructor to be invoked by `builder()`/`autoBuilder()`/`create()` entry-points.
        constructor {
            val creator = thisGraph.creator
            if (creator != null) {
                modifiers(/*package-private*/)
            } else {
                modifiers(PRIVATE)
            }
            val paramsNs = Namespace(prefix = "p")
            // Firstly - used parents
            thisGraph.usedParents.forEach { graph ->
                val name = paramsNs.name(graph.model.name)
                parameter(graph[ComponentImplClassName], name)
                +"this.${fieldNameFor(graph)} = $name"
            }
            if (creator != null) {
                // If creator is present, add parameters in order.
                val allInputs = creator.allInputs
                for (input in allInputs) {
                    val name = paramsNs.name(input.name)
                    val model = input.payload.model
                    parameter(model.typeName(), name)
                    val fieldName = inputsWithFieldNames[model] ?: continue  // Invalid - UB.
                    +"this.%N = %T.checkInputNotNull(%N)".formatCode(fieldName, Names.Checks, name)
                }
                for ((model, fieldName) in inputsWithFieldNames) {
                    // Generate all trivially constructable modules requiring instance that are not provided.
                    if (model is ModuleModel && model.isTriviallyConstructable &&
                        allInputs.none { it.payload.model == model }
                    ) {
                        +"this.%N = new %T()".formatCode(fieldName, model.typeName())
                    }
                }
            } else {
                // Add parameters for auto-creator (if non-root - UB)
                for ((inputModel, fieldName) in inputsWithFieldNames) {
                    val name = paramsNs.name(inputModel.name)
                    parameter(inputModel.typeName(), name)
                    if (inputModel is ModuleModel && inputModel.isTriviallyConstructable) {
                        // Trivially constructable modules are optional is auto-creator, check here
                        +"this.%N = %N != null ? %N : new %T()".formatCode(fieldName, name, name, inputModel.typeName())
                    } else {
                        +"this.%N = %N".formatCode(fieldName, name)
                    }
                }
            }
        }
    }

    private fun TypeSpecBuilder.generateBuilder(factory: ComponentFactoryWithBuilderModel) {
        nestedType {
            buildClass(implName) {
                modifiers(PRIVATE, FINAL, STATIC)
                implements(factory.typeName())

                val builderAccess = arrayListOf<String>()
                if (!thisGraph.isRoot) {
                    val paramsNs = Namespace(prefix = "f")
                    constructor {
                        thisGraph.usedParents.forEach { graph ->
                            val name = paramsNs.name(graph.model.name)
                            builderAccess += "this.$name"
                            val typeName = graph[ComponentImplClassName]
                            this@buildClass.field(typeName, name)
                            parameter(typeName, name)
                            +"this.$name = $name"
                        }
                    }
                }

                factory.factoryInputs.mapTo(builderAccess, ComponentFactoryModel.InputModel::name)
                with(Namespace("m")) {
                    factory.builderInputs.forEach { builderInput ->
                        val fieldName = name(builderInput.name)
                        builderAccess += "this.$fieldName"
                        field(builderInput.payload.model.typeName(), fieldName) {
                            modifiers(PRIVATE)
                        }
                        overrideMethod(builderInput.builderSetter) {
                            modifiers(PUBLIC)
                            +"this.$fieldName = %N".formatCode(builderInput.builderSetter.parameters.single().name)
                            if (!builderInput.builderSetter.returnType.isVoid) {
                                +"return this"
                            }
                        }
                    }
                }
                factory.factoryMethod?.let { factoryMethod ->
                    overrideMethod(factoryMethod) {
                        modifiers(PUBLIC)
                        +buildExpression {
                            +"return new %T(".formatCode(componentImplName)
                            join(builderAccess) { +it }
                            +")"
                        }
                    }
                }
            }
        }
        if (thisGraph.isRoot) {
            // ENTRY-POINT: See `Yatagan.builder()`
            method("builder") {
                modifiers(PUBLIC, STATIC)
                returnType(factory.typeName())
                +"return new %T()".formatCode(implName)
            }
        }
    }

    private fun TypeSpecBuilder.generateAutoBuilder() {
        val autoBuilderImplName = componentImplName.nestedClass("AutoBuilderImpl")
        nestedType {
            buildClass(autoBuilderImplName) {
                modifiers(PRIVATE, FINAL, STATIC)
                implements(Names.AutoBuilder(componentImplName))
                for ((input, fieldName) in inputsWithFieldNames) {
                    field(input.typeName(), fieldName) {
                        modifiers(PRIVATE)
                    }
                }

                method("provideInput") {
                    val i = TypeVariableName.get("I")
                    modifiers(PUBLIC, FINAL)
                    parameter(i, "input")
                    generic(i)
                    parameter(Names.Class(i), "inputClass")
                    returnType(Names.AutoBuilder(componentImplName))
                    annotation<Override>()
                    if (inputsWithFieldNames.isNotEmpty()) {
                        ifElseIfFlow(
                            args = inputsWithFieldNames.entries,
                            condition = { (input, _) -> +"inputClass == %T.class".formatCode(input.typeName()) },
                            block = { (input, fieldName) ->
                                +"this.%N = (%T) input".formatCode(fieldName, input.typeName())
                            },
                            elseBlock = {
                                +buildExpression {
                                    +"%T.reportUnexpectedAutoBuilderInput(inputClass, %T.asList("
                                        .formatCode(Names.Checks, Names.Arrays)
                                    join(inputsWithFieldNames.keys) { +"%T.class".formatCode(it.typeName()) }
                                    +"))"
                                }
                            },
                        )
                    } else {
                        +"%T.reportUnexpectedAutoBuilderInput(input.getClass(), %T.emptyList())"
                            .formatCode(Names.Checks, Names.Collections)
                    }
                    +"return this"
                }

                method("create") {
                    modifiers(PUBLIC, FINAL)
                    annotation<Override>()
                    returnType(componentImplName)
                    for ((input, fieldName) in inputsWithFieldNames) {
                        if (input is ModuleModel && input.isTriviallyConstructable)
                            continue  // Trivially constructable modules are optional

                        controlFlow("if (this.%N == null)".formatCode(fieldName)) {
                            +"%T.reportMissingAutoBuilderInput(%T.class)".formatCode(Names.Checks, input.typeName())
                        }
                    }
                    +buildExpression {
                        +"return new %T(".formatCode(componentImplName)
                        join(inputsWithFieldNames.values) { +"this.%N".formatCode(it) }
                        +")"
                    }
                }
            }
        }

        // ENTRY-POINT: See `Yatagan.autoBuilder()`
        method("autoBuilder") {
            modifiers(PUBLIC, STATIC)
            returnType(Names.AutoBuilder(componentImplName))
            +"return new %T()".formatCode(autoBuilderImplName)
        }
    }

    companion object Key : Extensible.Key<ComponentFactoryGenerator> {
        override val keyType get() = ComponentFactoryGenerator::class.java
    }
}
