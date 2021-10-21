package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ComponentFactoryModel
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
    private val inputFieldNames: Map<ComponentFactoryModel.Input, String> =
        (thisGraph.model.factory?.inputs?.asSequence() ?: emptySequence()).associateWith { input ->
            fieldsNs.name(input.paramName)
        }
    private val superComponentFieldNames: Map<BindingGraph, String> =
        thisGraph.usedParents.associateWith { graph: BindingGraph ->
            fieldsNs.name(graph.model.name)
        }

    val implName: ClassName = componentImplName.nestedClass("ComponentFactoryImpl")

    fun fieldNameFor(input: ComponentFactoryModel.Input) = checkNotNull(inputFieldNames[input])
    fun fieldNameFor(graph: BindingGraph) = checkNotNull(superComponentFieldNames[graph])

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
                factory.inputs.forEach { input ->
                    val name = paramsNs.name(input.paramName)
                    parameter(input.target.typeName(), name)
                    +"this.${fieldNameFor(input)} = $name"
                }
                thisGraph.usedParents.forEach { graph ->
                    val name = paramsNs.name(graph.model.name)
                    parameter(generators[graph].implName, name)
                    +"this.${fieldNameFor(graph)} = $name"
                }
            }
            nestedType {
                buildClass(implName) {
                    modifiers(PRIVATE, FINAL, STATIC)
                    implements(factory.name.asTypeName())

                    val usedParentsParamNames = arrayListOf<String>()
                    if (isSubComponentFactory) {
                        val paramsNs = Namespace(prefix = "f")
                        constructor {
                            thisGraph.usedParents.forEach { graph ->
                                val name = paramsNs.name(graph.model.name)
                                usedParentsParamNames += name
                                val typeName = generators[graph].implName
                                this@buildClass.field(typeName, name)
                                parameter(typeName, name)
                                +"this.$name = $name"
                            }
                        }
                    }

                    method("create") {
                        modifiers(PUBLIC)
                        annotation<Override>()
                        returnType(thisGraph.model.typeName())
                        factory.inputs.forEach { input ->
                            parameter(input.target.typeName(), input.paramName)
                        }
                        +buildExpression {
                            +"return new %T(".formatCode(componentImplName)
                            join(factory.inputs.asSequence()) { +it.paramName }
                            if (usedParentsParamNames.isNotEmpty()) {
                                if (factory.inputs.isNotEmpty()) {
                                    +", "
                                }
                                join(usedParentsParamNames.asSequence()) { +it }
                            }
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