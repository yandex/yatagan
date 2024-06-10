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

import com.yandex.yatagan.Component
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.jap.JavaxLexicalScope
import com.yandex.yatagan.lang.jap.asTypeElement
import com.yandex.yatagan.processor.common.BooleanOption
import com.yandex.yatagan.processor.common.Logger
import com.yandex.yatagan.processor.common.Options
import com.yandex.yatagan.processor.common.ProcessorDelegate
import com.yandex.yatagan.processor.common.initScopedOptions
import com.yandex.yatagan.processor.common.process
import java.io.Writer
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

class JapYataganProcessor : AbstractProcessor(), ProcessorDelegate<TypeElement> {
    override lateinit var logger: Logger
        private set
    override lateinit var options: Options
        private set
    override lateinit var lexicalScope: JavaxLexicalScope

    override fun createDeclaration(source: TypeElement) = lexicalScope.getTypeDeclaration(source)

    override fun getSourceFor(declaration: TypeDeclaration): TypeElement {
        return declaration.platformModel as TypeElement
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun getSupportedAnnotationTypes(): Set<String> = buildSet {
        add(Component::class.java.canonicalName)
        if (options[BooleanOption.DaggerCompatibilityMode])
            add("dagger.Component")
    }

    override fun getSupportedOptions(): Set<String> {
        return Options.all().mapTo(mutableSetOf()) { it.key }
    }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        logger = JapLogger(processingEnv.messager)
        options = Options(processingEnv.options)
        lexicalScope = JavaxLexicalScope(processingEnv.typeUtils, processingEnv.elementUtils)
        initScopedOptions(lexicalScope, this)
    }

    override fun openFileForGenerating(
        sources: Sequence<TypeElement>,
        packageName: String,
        className: String,
    ): Writer {
        val name = if (packageName.isNotEmpty()) "$packageName.$className" else className
        return processingEnv.filer.createSourceFile(name, sources.first()).openWriter().buffered()
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val elementsByAnnotation = buildSet {
            for (annotation in annotations) {
                addAll(roundEnv.getElementsAnnotatedWith(annotation))
            }
        }
        process(
            sources = elementsByAnnotation
                .map(Element::asTypeElement)
                .asSequence(),
            delegate = this,
        )
        return true
    }
}