package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.InstanceInput
import com.yandex.daggerlite.core.ModuleInstanceInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.generator.poetry.MethodSpecBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildClass
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.BindingGraph
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

internal class ComponentFactoryGenerator(
    private val thisGraph: BindingGraph,
    private val componentImplName: ClassName,
    fieldsNs: Namespace,
) : ComponentGenerator.Contributor {
    private val instanceFieldNames = hashMapOf<InstanceInput, String>()
    private val moduleInstanceFieldNames = hashMapOf<ModuleModel, String>()
    private val componentInstanceFieldNames = hashMapOf<ComponentDependencyInput, String>()
    private val inputFieldNames = mutableMapOf<ComponentFactoryModel.Input, String>()
    private val triviallyConstructableModules: Collection<ModuleModel>

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

        triviallyConstructableModules = thisGraph.modules.asSequence()
            .filter { module -> module.requiresInstance && module !in moduleInstanceFieldNames }
            .onEach { module ->
                check(module.isTriviallyConstructable) {
                    "module $module is not provided and can't be created on-the-fly"
                }
                val name = fieldsNs.name(module.name)
                moduleInstanceFieldNames[module] = name
            }.toList()
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
                field(Generators[input].implName, name) { modifiers(PRIVATE, FINAL) }
            }
            constructor {
                modifiers(PRIVATE)
                val paramsNs = Namespace(prefix = "p")
                // Firstly - used parents
                thisGraph.usedParents.forEach { graph ->
                    val name = paramsNs.name(graph.model.name)
                    parameter(Generators[graph].implName, name)
                    +"this.${fieldNameFor(graph)} = $name"
                }
                // Secondly and thirdly - factory inputs and builder inputs respectively.
                factory.allInputs.forEach { input ->
                    val name = paramsNs.name(input.name)
                    parameter(input.target.typeName(), name)
                    +"this.${inputFieldNames[input]} = $name"
                }
                generateTriviallyConstructableModules(constructorBuilder = this, builder = builder)
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
                                val typeName = Generators[graph].implName
                                this@buildClass.field(typeName, name)
                                parameter(typeName, name)
                                +"this.$name = $name"
                            }
                        }
                    }

                    factory.factoryInputs.mapTo(builderAccess, ComponentFactoryModel.Input::name)
                    with(Namespace("m")) {
                        factory.builderInputs.forEach { builderInput ->
                            val fieldName = name(builderInput.name)
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
                method("builder") {
                    modifiers(PUBLIC, STATIC)
                    returnType(factory.typeName())
                    +"return new %T()".formatCode(implName)
                }
            }
        } else {
            // TODO: generate default factory if explicit one is absent.
            constructor {
                modifiers(PUBLIC)
                generateTriviallyConstructableModules(constructorBuilder = this, builder = builder)
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
                    modifiers(PRIVATE, FINAL)
                }
            }
            with(constructorBuilder) {
                // MAYBE: Make this lazy?
                +"this.%N = new %T()".formatCode(fieldName, module.typeName())
            }
        }
    }
}
