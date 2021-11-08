package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.InstanceInput
import com.yandex.daggerlite.core.ModuleInstanceInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildClass
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

internal class ComponentFactoryGenerator(
    private val thisGraph: BindingGraph,
    private val componentImplName: ClassName,
    private val generators: Generators,
    fieldsNs: Namespace,
) : ComponentGenerator.Contributor {
    private val instanceFieldNames = hashMapOf<InstanceInput, String>()
    private val moduleInstanceFieldNames = hashMapOf<ModuleModel, String>()
    private val componentInstanceFieldNames = hashMapOf<ComponentDependencyInput, String>()
    private val inputFieldNames = mutableMapOf<ComponentFactoryModel.Input, String>()

    init {
        thisGraph.model.factory?.let { factory ->
            for (input in factory.allInputs) {
                val fieldName = fieldsNs.name(input.name)
                inputFieldNames[input] = fieldName
                when (input) {
                    is ComponentDependencyInput -> componentInstanceFieldNames[input] = fieldName
                    is InstanceInput -> instanceFieldNames[input] = fieldName
                    is ModuleInstanceInput -> moduleInstanceFieldNames[input.module] = fieldName
                }.let { /* exhaustive */ }
            }
        }
    }

    private val superComponentFieldNames: Map<BindingGraph, String> =
        thisGraph.usedParents.associateWith { graph: BindingGraph ->
            fieldsNs.name(graph.model.name)
        }

    val implName: ClassName = componentImplName.nestedClass("ComponentFactoryImpl")

    fun fieldNameFor(input: InstanceInput) = checkNotNull(instanceFieldNames[input])
    fun fieldNameFor(input: ComponentDependencyInput) = checkNotNull(componentInstanceFieldNames[input])
    fun fieldNameFor(graph: BindingGraph) = checkNotNull(superComponentFieldNames[graph])
    fun fieldNameFor(module: ModuleModel) = checkNotNull(moduleInstanceFieldNames[module])

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        val isSubComponentFactory = !thisGraph.model.isRoot
        val factory = thisGraph.model.factory
        if (factory != null) {
            inputFieldNames.forEach { (input, name) ->
                field(input.target.typeName(), name) { modifiers(PRIVATE, FINAL) }
            }
            superComponentFieldNames.forEach { (input, name) ->
                field(generators[input].implName, name) { modifiers(PRIVATE, FINAL) }
            }
            constructor {
                modifiers(PRIVATE)
                val paramsNs = Namespace(prefix = "p")
                // Firstly - used parents
                thisGraph.usedParents.forEach { graph ->
                    val name = paramsNs.name(graph.model.name)
                    parameter(generators[graph].implName, name)
                    +"this.${fieldNameFor(graph)} = $name"
                }
                // Secondly and thirdly - factory inputs and builder inputs respectively.
                factory.allInputs.forEach { input ->
                    val name = paramsNs.name(input.name)
                    parameter(input.target.typeName(), name)
                    +"this.${inputFieldNames[input]} = $name"
                }
            }
            nestedType {
                buildClass(implName) {
                    modifiers(PRIVATE, FINAL, STATIC)
                    implements(factory.typeName())

                    val builderAccess = arrayListOf<String>()
                    if (isSubComponentFactory) {
                        val paramsNs = Namespace(prefix = "f")
                        constructor {
                            thisGraph.usedParents.forEach { graph ->
                                val name = paramsNs.name(graph.model.name)
                                builderAccess += "this.$name"
                                val typeName = generators[graph].implName
                                this@buildClass.field(typeName, name)
                                parameter(typeName, name)
                                +"this.$name = $name"
                            }
                        }
                    }

                    with(Namespace("m")) {
                        factory.builderInputs.forEach { builderInput ->
                            val fieldName = name(builderInput.name, builderInput.target.name)
                            builderAccess += "this.$fieldName"
                            field(builderInput.target.typeName(), fieldName) {
                                modifiers(PRIVATE)
                            }
                            method(builderInput.name) {
                                modifiers(PUBLIC)
                                annotation<Override>()
                                returnType(factory.typeName())
                                parameter(builderInput.target.typeName(), "input")
                                +"this.$fieldName = input"
                                // TODO: support builder setters with `void` return type.
                                +"return this"
                            }
                        }
                    }
                    factory.factoryInputs.mapTo(builderAccess, ComponentFactoryModel.Input::name)

                    method(factory.factoryFunctionName) {
                        modifiers(PUBLIC)
                        annotation<Override>()
                        returnType(thisGraph.model.typeName())
                        factory.factoryInputs.forEach { input ->
                            parameter(input.target.typeName(), input.name)
                        }
                        +buildExpression {
                            +"return new %T(".formatCode(componentImplName)
                            join(builderAccess) { +it }
                            +")"
                        }
                    }
                }
            }
            if (!isSubComponentFactory) {
                method("factory") {
                    modifiers(PUBLIC, STATIC)
                    returnType(factory.typeName())
                    +"return new %T()".formatCode(implName)
                }
            }
        }
        // TODO: generate default factory if explicit one is absent.
    }
}
