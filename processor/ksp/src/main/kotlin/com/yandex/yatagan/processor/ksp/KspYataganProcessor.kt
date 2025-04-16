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

package com.yandex.yatagan.processor.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.yatagan.Component
import com.yandex.yatagan.base.ifOrElseNull
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.ksp.KspLexicalScope
import com.yandex.yatagan.processor.common.BooleanOption
import com.yandex.yatagan.processor.common.Logger
import com.yandex.yatagan.processor.common.Options
import com.yandex.yatagan.processor.common.ProcessorDelegate
import com.yandex.yatagan.processor.common.initScopedOptions
import com.yandex.yatagan.processor.common.process
import java.io.Writer

internal class KspYataganProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor, ProcessorDelegate<KSClassDeclaration> {
    override val logger: Logger = KspLogger(environment.logger)
    override val options: Options = Options(environment.options)
    override lateinit var lexicalScope: KspLexicalScope

    override fun process(resolver: Resolver): List<KSAnnotated> {
        lexicalScope = KspLexicalScope(resolver, environment)
        initScopedOptions(lexicalScope, this)
        process(
            sources = sequenceOf(
                resolver.getSymbolsWithAnnotation(Component::class.java.canonicalName),
                ifOrElseNull(options[BooleanOption.DaggerCompatibilityMode]) {
                    resolver.getSymbolsWithAnnotation("dagger.Component")
                }
            ).filterNotNull().flatten().filterIsInstance<KSClassDeclaration>().distinct(),
            delegate = this,
        )
        return emptyList()
    }

    override fun createDeclaration(source: KSClassDeclaration) = lexicalScope.getTypeDeclaration(source)

    override fun getSourceFor(declaration: TypeDeclaration): KSClassDeclaration {
        return declaration.platformModel as KSClassDeclaration
    }

    override fun openFileForGenerating(
        sources: Sequence<KSClassDeclaration>,
        packageName: String,
        className: String,
    ): Writer {
        val newFile = environment.codeGenerator.createNewFile(
            Dependencies(
                aggregating = false,
            ),
            packageName = packageName,
            fileName = className,
            extensionName = "java",
        )
        environment.codeGenerator.associateWithClasses(
            classes = sources.toList(),
            packageName = packageName,
            fileName = className,
            extensionName = "java",
        )
        return newFile.bufferedWriter()
    }
}

