package com.yandex.daggerlite.jap

import com.google.auto.common.BasicAnnotationProcessor
import com.google.common.collect.ImmutableSetMultimap
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.jap.lang.JavaxModelFactoryImpl
import com.yandex.daggerlite.jap.lang.ProcessingUtils
import com.yandex.daggerlite.jap.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.jap.lang.asTypeElement
import com.yandex.daggerlite.process.Logger
import com.yandex.daggerlite.process.ProcessorDelegate
import com.yandex.daggerlite.process.process
import java.io.Writer
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

internal class JapComponentProcessingStep(
    messager: Messager,
    private val filer: Filer,
    private val types: Types,
    private val elements: Elements,
) : BasicAnnotationProcessor.Step, ProcessorDelegate<TypeElement> {
    override val langModelFactory by lazy(::JavaxModelFactoryImpl)
    override val logger: Logger = JapLogger(messager)

    override fun annotations(): Set<String> = setOf(Component::class.qualifiedName!!)

    override fun createDeclaration(source: TypeElement) = TypeDeclarationLangModel(source)

    override fun openFileForGenerating(source: TypeElement, packageName: String, className: String): Writer {
        return filer.createSourceFile("$packageName.$className", source).openWriter()
    }

    override fun process(elementsByAnnotation: ImmutableSetMultimap<String, Element>): Set<Element> {
        ProcessingUtils(types, elements).use {
            process(
                sources = elementsByAnnotation.values()
                    .map(Element::asTypeElement)
                    .asSequence(),
                delegate = this,
            )
        }
        return emptySet()
    }
}