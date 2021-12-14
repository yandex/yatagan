package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
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
    private val instanceFieldNames = hashMapOf<NodeModel, String>()
    private val moduleInstanceFieldNames = hashMapOf<ModuleModel, String>()
    private val componentInstanceFieldNames = hashMapOf<ComponentDependencyModel, String>()
    private val inputFieldNames = mutableMapOf<ComponentFactoryModel.InputModel, String>()
    private val triviallyConstructableModules: Collection<ModuleModel>

    init {
        thisGraph.model.factory?.let { factory ->
            for (input in factory.allInputs) {
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

    fun fieldNameFor(boundInstance: NodeModel) = checkNotNull(instanceFieldNames[boundInstance])
    fun fieldNameFor(dependency: ComponentDependencyModel) = checkNotNull(componentInstanceFieldNames[dependency])
    fun fieldNameFor(graph: BindingGraph) = checkNotNull(superComponentFieldNames[graph])
    fun fieldNameFor(module: ModuleModel) = checkNotNull(moduleInstanceFieldNames[module])

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        val isSubComponentFactory = !thisGraph.model.isRoot
        val factory = thisGraph.model.factory
        if (factory != null) {
            inputFieldNames.forEach { (input, name) ->
                field(input.payload.typeName(), name) { modifiers(PRIVATE, FINAL) }
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
                    parameter(input.payload.typeName(), name)
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

                    factory.factoryInputs.mapTo(builderAccess, ComponentFactoryModel.InputModel::name)
                    with(Namespace("m")) {
                        factory.builderInputs.forEach { builderInput ->
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

                    overrideMethod(factory.factoryMethod) {
                        modifiers(PUBLIC)
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
