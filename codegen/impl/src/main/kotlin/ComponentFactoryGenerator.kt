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

import com.yandex.yatagan.codegen.poetry.Access
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.nestedClass
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.ClassBackedModel
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.allInputs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ComponentFactoryGenerator @Inject constructor(
    private val thisGraph: BindingGraph,
    private val componentImplName: ClassName,
    private val options: ComponentGenerator.Options,
    @FieldsNamespace fieldsNs: Namespace,
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
            field(
                type = TypeName.Inferred(inputModel.type),
                name = name,
                isMutable = false,
                access = Access.Internal,
            ) {}
        }
        superComponentFieldNames.forEach { (input, name) ->
            field(
                type = input[GeneratorComponent].implementationClassName,
                name = name,
                isMutable = false,
                access = Access.Internal,
            ) {}
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
        val creator = thisGraph.creator
        primaryConstructor(
            access = Access.Internal, // if (creator != null) Access.Internal else Access.Private,
        ) {
            val paramsNs = Namespace(prefix = "p")
            // Firstly - used parents
            thisGraph.usedParents.forEach { graph ->
                val name = paramsNs.name(graph.model.name)
                parameter(graph[GeneratorComponent].implementationClassName, name)
                code {
                    appendStatement { append("this.").appendName(fieldNameFor(graph)).append(" = ").appendName(name) }
                }
            }
            if (creator != null) {
                // If creator is present, add parameters in order.
                val allInputs = creator.allInputs
                for (input in allInputs) {
                    val name = paramsNs.name(input.name)
                    val model = input.payload.model
                    parameter(
                        type = TypeName.Nullable(TypeName.Inferred(model.type)),
                        name = name,
                    )
                    val fieldName = inputsWithFieldNames[model] ?: continue  // Invalid - UB.
                    code {
                        appendStatement {
                            append("this.").appendName(fieldName).append(" = ")
                            if (options.enableProvisionNullChecks) {
                                appendCheckInputNotNull { appendName(name) }
                            } else {
                                appendName(name)
                            }
                        }
                    }
                }
                for ((model, fieldName) in inputsWithFieldNames) {
                    // Generate all trivially constructable modules requiring instance that are not provided.
                    if (model is ModuleModel && model.isTriviallyConstructable &&
                        allInputs.none { it.payload.model == model }
                    ) {
                        code {
                            appendStatement {
                                append("this.").appendName(fieldName).append(" = ")
                                    .appendObjectCreation(type = TypeName.Inferred(model.type))
                            }
                        }
                    }
                }
            } else {
                // Add parameters for auto-creator (if non-root - UB)
                for ((inputModel, fieldName) in inputsWithFieldNames) {
                    val triviallyConstructableModule = inputModel is ModuleModel && inputModel.isTriviallyConstructable
                    val name = paramsNs.name(inputModel.name)
                    var type: TypeName = TypeName.Inferred(inputModel.type)
                    if (triviallyConstructableModule) {
                        type = TypeName.Nullable(type)
                    }
                    parameter(
                        type = type,
                        name = name,
                    )
                    code {
                        appendStatement {
                            append("this.").appendName(fieldName).append(" = ")
                            if (triviallyConstructableModule) {
                                // Trivially constructable modules are optional in auto-creator, check here
                                appendTernaryExpression(
                                    condition = { appendName(name).append(" != null") },
                                    ifTrue = { appendName(name) },
                                    ifFalse = { appendObjectCreation(TypeName.Inferred(inputModel.type)) },
                                )
                            } else {
                                appendName(name)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun TypeSpecBuilder.generateBuilder(factory: ComponentFactoryWithBuilderModel) {
        nestedClass(
            name = implName,
            access = Access.Internal, // if(thisGraph.isRoot) Access.Private else Access.Internal,
            isInner = false,
        ) {
            implements(TypeName.Inferred(factory.type))

            val builderAccess = arrayListOf<ExpressionBuilder.() -> Unit>()
            if (!thisGraph.isRoot) {
                val paramsNs = Namespace(prefix = "f")
                primaryConstructor(
                    access = Access.Internal,
                ) {
                    thisGraph.usedParents.forEach { graph ->
                        val name = paramsNs.name(graph.model.name)
                        builderAccess += { append("this.").appendName(name) }
                        val typeName = graph[GeneratorComponent].implementationClassName
                        this@nestedClass.field(
                            type = typeName,
                            name = name,
                            access = Access.Internal,
                            isMutable = false,
                        ) {}
                        parameter(typeName, name)
                        code {
                            appendStatement {
                                append("this.").appendName(name).append(" = ").appendName(name)
                            }
                        }
                    }
                }
            }

            factory.factoryInputs.mapTo(builderAccess) { { appendName(it.name) } }
            with(Namespace("m")) {
                factory.builderInputs.forEach { builderInput ->
                    val fieldName = name(builderInput.name)
                    builderAccess += { append("this.").appendName(fieldName) }
                    field(
                        type = TypeName.Nullable(TypeName.Inferred(builderInput.payload.model.type)),
                        name = fieldName,
                        access = Access.Private,
                        isMutable = true,
                    ) {}
                    overrideMethod(builderInput.builderSetter) {
                        code {
                            appendStatement {
                                append("this.").appendName(fieldName).append(" = ").appendName(
                                    builderInput.builderSetter.parameters.single().name
                                )
                            }
                            if (!builderInput.builderSetter.returnType.isVoid) {
                                appendReturnStatement { append("this") }
                            }
                        }
                    }
                }
            }
            factory.factoryMethod?.let { factoryMethod ->
                overrideMethod(factoryMethod) {
                    code {
                        appendReturnStatement {
                            appendObjectCreation(
                                type = componentImplName,
                                argumentCount = builderAccess.size,
                                argument = { builderAccess[it]() }
                            )
                        }
                    }
                }
            }
        }
        if (thisGraph.isRoot) {
            // ENTRY-POINT: See `Yatagan.builder()`
            method(
                name = "builder",
                access = Access.Public,
                isStatic = true,
            ) {
                returnType(TypeName.Inferred(factory.type))
                code {
                    appendReturnStatement {
                        appendObjectCreation(implName)
                    }
                }
            }
        }
    }

    private fun TypeSpecBuilder.generateAutoBuilder() {
        val autoBuilderImplName = componentImplName.nestedClass("AutoBuilderImpl")
        nestedClass(
            name = autoBuilderImplName,
            access = Access.Private,
            isInner = false,
        ) {
            implements(TypeName.AutoBuilder(componentImplName))
            for ((input, fieldName) in inputsWithFieldNames) {
                field(
                    type = TypeName.Nullable(TypeName.Inferred(input.type)),
                    name = fieldName,
                    access = Access.Private,
                    isMutable = true,
                ) {}
            }

            method(
                name = "provideInput",
                access = Access.Public,
            ) {
                val i = TypeName.TypeVariable("I")
                parameter(i, "input")
                generic(i.copy(extendsAnyWildcard = true))
                parameter(TypeName.Class(i), "clazz")
                returnType(TypeName.AutoBuilder(componentImplName))
                manualOverride()
                code {
                    if (inputsWithFieldNames.isNotEmpty()) {
                        appendIfElseIfControlFlow(
                            args = inputsWithFieldNames.entries,
                            condition = { (input, _) -> 
                                append("clazz == ").appendClassLiteral(TypeName.Inferred(input.type))
                            },
                            block = { (input, fieldName) ->
                                appendStatement {
                                    append("this.").appendName(fieldName).append(" = ").appendCast(
                                        asType = TypeName.Inferred(input.type),
                                    ) { append("input") }
                                }
                            },
                            fallback = {
                                appendStatement {
                                    appendReportUnexpectedBuilderInput(
                                        inputClassArgument = { append("clazz") },
                                        inputsWithFieldNames.keys.map { TypeName.Inferred(it.type) },
                                    )
                                }
                            },
                        )
                    } else {
                        appendStatement {
                            appendReportUnexpectedBuilderInput(
                                inputClassArgument = { append("input.getClass()") },
                                emptyList(),
                            )
                        }
                    }
                    appendReturnStatement { append("this") }
                }
            }

            method(
                name = "create",
                access = Access.Public,
            ) {
                manualOverride()
                returnType(componentImplName)
                code {
                    for ((input, fieldName) in inputsWithFieldNames) {
                        if (input is ModuleModel && input.isTriviallyConstructable)
                            continue  // Trivially constructable modules are optional

                        appendIfControlFlow(
                            condition = { append("this.").appendName(fieldName).append(" == null") },
                            ifTrue = {
                                appendStatement {
                                    appendReportMissingBuilderInput(TypeName.Inferred(input.type))
                                }
                            }
                        )
                    }
                    appendReturnStatement {
                        val inputs = inputsWithFieldNames.values.toList()
                        appendObjectCreation(
                            type = componentImplName,
                            argumentCount = inputs.size,
                            argument = {
                                append("this.").appendName(inputs[it])
                            },
                        )
                    }
                }
            }
        }

        // ENTRY-POINT: See `Yatagan.autoBuilder()`
        method(
            name = "autoBuilder",
            access = Access.Public,
            isStatic = true,
        ) {
            returnType(TypeName.AutoBuilder(componentImplName))
            code {
                appendReturnStatement {
                    appendObjectCreation(autoBuilderImplName)
                }
            }
        }
    }
}
