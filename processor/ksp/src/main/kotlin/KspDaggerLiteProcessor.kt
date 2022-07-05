package com.yandex.daggerlite.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.use
import com.yandex.daggerlite.ksp.lang.KspModelFactoryImpl
import com.yandex.daggerlite.ksp.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.ksp.lang.Utils
import com.yandex.daggerlite.process.Logger
import com.yandex.daggerlite.process.Options
import com.yandex.daggerlite.process.ProcessorDelegate
import com.yandex.daggerlite.process.process
import java.io.Writer

internal class KspDaggerLiteProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor, ProcessorDelegate<KSClassDeclaration> {
    override val logger: Logger = KspLogger(environment.logger)
    override val options: Options = Options(environment.options)

    init {
        if (options[Options.UseParallelProcessing]) {
            environment.logger.warn("KSP doesn't support parallel processing as of now. The option is ignored.")
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        Utils.init(resolver).use {
            LangModelFactory.use(KspModelFactoryImpl()) {
                process(
                    sources = resolver.getSymbolsWithAnnotation(Component::class.java.canonicalName)
                        .filterIsInstance<KSClassDeclaration>(),
                    delegate = this,
                    // KSP model doesn't support multi-thread access.
                    // https://github.com/google/ksp/issues/311
                    useParallelProcessing = false,
                )
                return emptyList()
            }
        }
    }

    override fun createDeclaration(source: KSClassDeclaration) = TypeDeclarationLangModel(source)

    override fun getSourceFor(declaration: TypeDeclarationLangModel): KSClassDeclaration {
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

