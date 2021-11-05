package com.yandex.daggerlite.jap

import com.google.auto.common.BasicAnnotationProcessor
import com.google.common.collect.ImmutableSetMultimap
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.BindingGraph
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.generator.Language
import com.yandex.daggerlite.jap.lang.ProcessingUtils
import com.yandex.daggerlite.jap.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.jap.lang.asTypeElement
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

internal class JapComponentProcessingStep(
    private val filer: Filer,
    private val messager: Messager,
    private val types: Types,
    private val elements: Elements,
) : BasicAnnotationProcessor.Step {

    override fun annotations(): Set<String> = setOf(Component::class.qualifiedName!!)

    override fun process(elementsByAnnotation: ImmutableSetMultimap<String, Element>): Set<Element> {
        ProcessingUtils(types, elements).use {
            ObjectCacheRegistry.use {
                for (element: Element in elementsByAnnotation.values()) {
                    val model = ComponentModel(TypeDeclarationLangModel(element.asTypeElement()))
                    if (!model.isRoot) {
                        continue
                    }
                    val graph = BindingGraph(
                        root = model,
                    )
                    if (graph.missingBindings.isNotEmpty()) {
                        graph.missingBindings.forEach { node ->
                            messager.printMessage(Diagnostic.Kind.ERROR, "Missing binding for $node")
                        }
                        continue
                    }
                    ComponentGeneratorFacade(graph).run {
                        if (targetLanguage != Language.Java)
                            throw RuntimeException("Jap driver supports only java files generating")

                        val file = filer.createSourceFile(
                            "$targetPackageName.$targetClassName",
                            element,
                        )
                        file.openWriter().use(::generateTo)
                    }
                }
            }
        }
        return emptySet()
    }
}