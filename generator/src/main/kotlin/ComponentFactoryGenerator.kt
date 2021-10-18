package com.yandex.dagger3.generator

import com.squareup.javapoet.ClassName
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.core.ComponentFactoryModel
import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.generator.poetry.TypeSpecBuilder
import com.yandex.dagger3.generator.poetry.buildClass
import com.yandex.dagger3.generator.poetry.buildExpression
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

// MAYBE: split this into two implementations: for component and for subcomponent
internal class ComponentFactoryGenerator(
    private val graph: BindingGraph,
    private val componentImplName: ClassName,
    private val generators: Map<ComponentModel, ComponentGenerator>,
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

    val factoryImplName: ClassName = componentImplName.nestedClass("ComponentFactoryImpl")

    fun fieldNameFor(binding: ComponentFactoryModel.Input) = checkNotNull(inputFieldNames[binding])
    fun fieldNameFor(parentGraph: BindingGraph) = checkNotNull(superComponentFieldNames[parentGraph])

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        val isSubComponentFactory = !graph.component.isRoot
        val factory = graph.component.factory
        if (factory != null) {
            inputFieldNames.forEach { (input, name) ->
                field(input.target.typeName(), name) { modifiers(PRIVATE, FINAL) }
            }
            superComponentFieldNames.forEach { (input, name) ->
                field(checkNotNull(generators[input.component]).targetClassName, name) { modifiers(PRIVATE, FINAL) }
            }
            constructor {
                modifiers(PRIVATE)
                val paramsNs = Namespace(prefix = "p")
                factory.inputs.forEach { input ->
                    val name = paramsNs.name(input.paramName)
                    parameter(input.target.typeName(), name)
                    +"this.${fieldNameFor(input)} = $name"
                }
                graph.usedParents.forEach { graph ->
                    val name = paramsNs.name(graph.component.name)
                    parameter(checkNotNull(generators[graph.component]).targetClassName, name)
                    +"this.${fieldNameFor(graph)} = $name"
                }
            }
            nestedType {
                buildClass(factoryImplName) {
                    modifiers(PRIVATE, FINAL, STATIC)
                    implements(factory.name.asTypeName())

                    val usedParentsParamNames = arrayListOf<String>()
                    if (isSubComponentFactory) {
                        val paramsNs = Namespace(prefix = "f")
                        constructor {
                            graph.usedParents.forEach { graph ->
                                val name = paramsNs.name(graph.component.name)
                                usedParentsParamNames += name
                                val typeName = checkNotNull(generators[graph.component]).targetClassName
                                this@buildClass.field(typeName, name)
                                parameter(typeName, name)
                                +"this.$name = $name"
                            }
                        }
                    }

                    method("create") {
                        modifiers(PUBLIC)
                        annotation<Override>()
                        returnType(graph.component.typeName())
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
                    +"return new %T()".formatCode(factoryImplName)
                }
            }
        }
    }
}