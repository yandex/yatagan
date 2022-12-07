/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.processor.jap

import com.google.auto.common.BasicAnnotationProcessor
import com.google.common.collect.ImmutableSetMultimap
import com.yandex.yatagan.Component
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.jap.JavaxModelFactoryImpl
import com.yandex.yatagan.lang.jap.ProcessingUtils
import com.yandex.yatagan.lang.jap.TypeDeclaration
import com.yandex.yatagan.lang.jap.asTypeElement
import com.yandex.yatagan.lang.use
import com.yandex.yatagan.processor.common.Logger
import com.yandex.yatagan.processor.common.Options
import com.yandex.yatagan.processor.common.ProcessorDelegate
import com.yandex.yatagan.processor.common.process
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
    options: Map<String, String>,
) : BasicAnnotationProcessor.Step, ProcessorDelegate<TypeElement> {
    override val logger: Logger = JapLogger(messager)

    override val options: Options = Options(options)

    override fun annotations(): Set<String> = setOf(Component::class.qualifiedName!!)

    override fun createDeclaration(source: TypeElement) = TypeDeclaration(source)

    override fun getSourceFor(declaration: TypeDeclaration): TypeElement {
        return declaration.platformModel as TypeElement
    }

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
                )
            }
        }
        return emptySet()
    }
}