package com.yandex.dagger3.generator

import com.squareup.javapoet.ClassName
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.core.ComponentFactoryModel
import com.yandex.dagger3.generator.poetry.TypeSpecBuilder
import com.yandex.dagger3.generator.poetry.buildClass
import com.yandex.dagger3.generator.poetry.buildExpression
import javax.lang.model.element.Modifier

internal class ComponentFactoryGenerator(
    private val graph: BindingGraph,
    private val componentImplName: ClassName,
    fieldsNs: Namespace,
) : ComponentGenerator.Contributor {
    private val inputFieldNames: Map<ComponentFactoryModel.Input, String> =
        (graph.component.factory?.inputs?.asSequence() ?: emptySequence()).associateWith { input ->
            fieldsNs.name(input.paramName)
        }
    private val superComponentFieldNames: Map<BindingGraph, String> =
        graph.usedParents.associateWith { graph ->
            fieldsNs.name(graph.component.name)
        }

    fun fieldNameFor(binding: ComponentFactoryModel.Input) = checkNotNull(inputFieldNames[binding])
    fun fieldNameFor(parentGraph: BindingGraph) = checkNotNull(superComponentFieldNames[parentGraph])

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        val factory = graph.component.factory
        if (factory != null) {
            val factoryImplName = componentImplName.nestedClass("ComponentFactoryImpl")
            inputFieldNames.forEach { (input, name) ->
                field(input.target.typeName(), name) { modifiers(Modifier.PRIVATE, Modifier.FINAL) }
            }
            superComponentFieldNames.forEach { (input, name) ->
                field(input.component.typeName(), name) { modifiers(Modifier.PRIVATE, Modifier.FINAL) }
            }
            constructor {
                modifiers(Modifier.PRIVATE)
                val paramsNs = Namespace()
                factory.inputs.forEach { input ->
                    val name = paramsNs.name(input.paramName)
                    parameter(input.target.typeName(), name)
                    +"this.${fieldNameFor(input)} = $name"
                }
                graph.usedParents.forEach { graph ->
                    val name = paramsNs.name(graph.component.typeName())
                    parameter(graph.component.typeName(), name)
                    +"this.${fieldNameFor(graph)} = $name"
                }
            }
            nestedType {
                buildClass(factoryImplName) {
                    modifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                    implements(factory.name.asTypeName())

                    method("create") {
                        modifiers(Modifier.PUBLIC)
                        annotation<Override>()
                        returnType(graph.component.name.asTypeName())
                        factory.inputs.forEach { input ->
                            parameter(input.target.name.asTypeName(), input.paramName)
                        }
                        +buildExpression {
                            +"return new %T(".formatCode(componentImplName)
                            join(factory.inputs.asSequence()) { +it.paramName }
                            +")"
                        }
                    }
                }
            }
            method("factory") {
                modifiers(Modifier.PUBLIC, Modifier.STATIC)
                returnType(factory.name.asTypeName())
                +"return new %T()".formatCode(factoryImplName)
            }
        }
    }
}