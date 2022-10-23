package com.yandex.daggerlite.processor.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.lang.LangModelFactory
import com.yandex.daggerlite.lang.TypeDeclaration
import com.yandex.daggerlite.lang.ksp.KspModelFactoryImpl
import com.yandex.daggerlite.lang.ksp.ProcessingUtils
import com.yandex.daggerlite.lang.ksp.TypeDeclaration
import com.yandex.daggerlite.lang.use
import com.yandex.daggerlite.processor.common.Logger
import com.yandex.daggerlite.processor.common.Options
import com.yandex.daggerlite.processor.common.ProcessorDelegate
import com.yandex.daggerlite.processor.common.process
import java.io.Writer

internal class KspDaggerLiteProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor, ProcessorDelegate<KSClassDeclaration> {
    override val logger: Logger = KspLogger(environment.logger)
    override val options: Options = Options(environment.options)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        ProcessingUtils(resolver).use {
            LangModelFactory.use(KspModelFactoryImpl()) {
                process(
                    sources = resolver.getSymbolsWithAnnotation(Component::class.java.canonicalName)
                        .filterIsInstance<KSClassDeclaration>(),
                    delegate = this,
                )
                return emptyList()
            }
        }
    }

    override fun createDeclaration(source: KSClassDeclaration) = TypeDeclaration(source)

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

