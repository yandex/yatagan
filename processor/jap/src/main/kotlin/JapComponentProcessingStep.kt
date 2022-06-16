package com.yandex.daggerlite.jap

import com.google.auto.common.BasicAnnotationProcessor
import com.google.common.collect.ImmutableSetMultimap
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.use
import com.yandex.daggerlite.jap.lang.JavaxModelFactoryImpl
import com.yandex.daggerlite.jap.lang.ProcessingUtils
import com.yandex.daggerlite.jap.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.jap.lang.asTypeElement
import com.yandex.daggerlite.process.Logger
import com.yandex.daggerlite.process.Options
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
    filer: Filer,
    types: Types,
    elements: Elements,
    options: Map<String, String>,
) : BasicAnnotationProcessor.Step, ProcessorDelegate<TypeElement> {
    override val logger: Logger = JapLogger(messager)

    override val options: Options = Options(options)

    private val useParallelProcessing by Options.UseParallelProcessing

    private val filer: Filer = if (useParallelProcessing) ThreadSafeFiler(filer) else filer
    private val types: Types = if (useParallelProcessing) ThreadSafeTypes(types) else types
    private val elements: Elements = if (useParallelProcessing) ThreadSafeElements(elements) else elements

    override fun annotations(): Set<String> = setOf(Component::class.qualifiedName!!)

    override fun createDeclaration(source: TypeElement) = TypeDeclarationLangModel(source)

    override fun openFileForGenerating(
        sources: Sequence<TypeElement>,
        packageName: String,
        className: String,
    ): Writer {
        val name = if (packageName.isNotEmpty()) "$packageName.$className" else className
        return filer.createSourceFile(name, sources.first()).openWriter().buffered()
    }

    override fun process(elementsByAnnotation: ImmutableSetMultimap<String, Element>): Set<Element> {
        ProcessingUtils(types, elements).use {
            LangModelFactory.use(JavaxModelFactoryImpl()) {
                process(
                    sources = elementsByAnnotation.values()
                        .map(Element::asTypeElement)
                        .asSequence(),
                    delegate = this,
                    useParallelProcessing = useParallelProcessing,
                )
            }
        }
        return emptySet()
    }
}