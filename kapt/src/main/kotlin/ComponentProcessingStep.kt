package com.yandex.daggerlite.compiler

import com.google.auto.common.BasicAnnotationProcessor
import com.google.common.collect.ImmutableSetMultimap
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import dagger.Component
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

class ComponentProcessingStep(
    private val filer: Filer,
    private val types: Types,
    private val elements: Elements,
) : BasicAnnotationProcessor.Step {

    override fun annotations(): Set<String> = setOf(Component::class.qualifiedName!!)

    override fun process(elementsByAnnotation: ImmutableSetMultimap<String, Element>): Set<Element> {
        elementsByAnnotation
            .values()
            .stream()
            .map(Element::asTypeElement)
            .filter(TypeElement::isRoot)
            .map { JavaxComponentModel(it, types, elements) }
            .forEach { model ->
                val graph = BindingGraph(model)
                val generator = ComponentGeneratorFacade(graph)

                generator.generateFile(filer)
            }

        return emptySet()
    }
}