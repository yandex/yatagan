package com.yandex.yatagan.processor.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.yatagan.Component
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.ksp.KspModelFactoryImpl
import com.yandex.yatagan.lang.ksp.ProcessingUtils
import com.yandex.yatagan.lang.ksp.TypeDeclaration
import com.yandex.yatagan.lang.use
import com.yandex.yatagan.processor.common.Logger
import com.yandex.yatagan.processor.common.Options
import com.yandex.yatagan.processor.common.ProcessorDelegate
import com.yandex.yatagan.processor.common.process
import java.io.Writer

internal class KspYataganProcessor(
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

