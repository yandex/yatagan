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
import com.yandex.yatagan.codegen.poetry.MethodSpecBuilder
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.buildClass
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.Extensible
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload
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
    private val instanceFieldNames = hashMapOf<NodeModel, String>()
    private val moduleInstanceFieldNames = hashMapOf<ModuleModel, String>()
    private val componentInstanceFieldNames = hashMapOf<ComponentDependencyModel, String>()
    private val inputFieldNames = mutableMapOf<ComponentFactoryModel.InputModel, String>()
    private val triviallyConstructableModules: Collection<ModuleModel>

    init {
        thisGraph.creator?.let { creator ->
            for (input in creator.allInputs) {
                val fieldName = fieldsNs.name(input.name)
                inputFieldNames[input] = fieldName
                when (val payload = input.payload) {
                    is InputPayload.Dependency -> componentInstanceFieldNames[payload.dependency] = fieldName
                    is InputPayload.Instance -> instanceFieldNames[payload.node] = fieldName
                    is InputPayload.Module -> moduleInstanceFieldNames[payload.module] = fieldName
                }.let { /* exhaustive */ }
            }
        }

        triviallyConstructableModules = thisGraph.modules.asSequence()
            .filter { module -> module.requiresInstance && module !in moduleInstanceFieldNames }
            .onEach { module ->
                // Such module must be trivially constructable, it's validated.
                val name = fieldsNs.name(module.name)
                moduleInstanceFieldNames[module] = name
            }.toList()
    }

    private val superComponentFieldNames: Map<BindingGraph, String> =
        thisGraph.usedParents.associateWith { graph: BindingGraph ->
            fieldsNs.name(graph.model.name)
        }

    val implName: ClassName = componentImplName.nestedClass("ComponentFactoryImpl")

    fun fieldNameFor(boundInstance: NodeModel) = checkNotNull(instanceFieldNames[boundInstance])
    fun fieldNameFor(dependency: ComponentDependencyModel) = checkNotNull(componentInstanceFieldNames[dependency])
    fun fieldNameFor(graph: BindingGraph) = checkNotNull(superComponentFieldNames[graph])
    fun fieldNameFor(module: ModuleModel) = checkNotNull(moduleInstanceFieldNames[module])

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        val isSubComponentFactory = !thisGraph.isRoot
        val creator = thisGraph.creator
        if (creator != null) {
            inputFieldNames.forEach { (input, name) ->
                field(input.payload.typeName(), name) { modifiers(/*package-private*/ FINAL) }
            }
            superComponentFieldNames.forEach { (input, name) ->
                field(input[ComponentImplClassName], name) { modifiers(/*package-private*/ FINAL) }
            }
            constructor {
                modifiers(/*package-private*/)
                val paramsNs = Namespace(prefix = "p")
                // Firstly - used parents
                thisGraph.usedParents.forEach { graph ->
                    val name = paramsNs.name(graph.model.name)
                    parameter(graph[ComponentImplClassName], name)
                    +"this.${fieldNameFor(graph)} = $name"
                }
                // Secondly and thirdly - factory inputs and builder inputs respectively.
                creator.allInputs.forEach { input ->
                    val name = paramsNs.name(input.name)
                    parameter(input.payload.typeName(), name)
                    +"this.%N = %T.checkInputNotNull(%N)".formatCode(inputFieldNames[input]!!, Names.Checks, name)
                }
                generateTriviallyConstructableModules(constructorBuilder = this, builder = builder)
            }
            nestedType {
                buildClass(implName) {
                    modifiers(PRIVATE, FINAL, STATIC)
                    implements(creator.typeName())

                    val builderAccess = arrayListOf<String>()
                    if (isSubComponentFactory) {
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

                    creator.factoryInputs.mapTo(builderAccess, ComponentFactoryModel.InputModel::name)
                    with(Namespace("m")) {
                        creator.builderInputs.forEach { builderInput ->
                            val fieldName = name(builderInput.name)
                            builderAccess += "this.$fieldName"
                            field(builderInput.payload.typeName(), fieldName) {
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
                    creator.factoryMethod?.let { factoryMethod ->
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
            if (!isSubComponentFactory) {
                method("builder") {
                    modifiers(PUBLIC, STATIC)
                    returnType(creator.typeName())
                    +"return new %T()".formatCode(implName)
                }
            }
        } else {
            constructor {
                modifiers(PRIVATE)
                generateTriviallyConstructableModules(constructorBuilder = this, builder = builder)
            }

            method("create") {
                modifiers(PUBLIC, STATIC)
                returnType(thisGraph.model.typeName())
                +"return new %T()".formatCode(componentImplName)
            }
        }
    }

    private fun generateTriviallyConstructableModules(
        constructorBuilder: MethodSpecBuilder,
        builder: TypeSpecBuilder,
    ) {
        triviallyConstructableModules.forEach { module ->
            val fieldName = moduleInstanceFieldNames[module]!!
            with(builder) {
                field(module.typeName(), fieldName) {
                    modifiers(/*package-private*/ FINAL)
                }
            }
            with(constructorBuilder) {
                // MAYBE: Make this lazy?
                +"this.%N = new %T()".formatCode(fieldName, module.typeName())
            }
        }
    }

    companion object Key : Extensible.Key<ComponentFactoryGenerator> {
        override val keyType get() = ComponentFactoryGenerator::class.java
    }
}
