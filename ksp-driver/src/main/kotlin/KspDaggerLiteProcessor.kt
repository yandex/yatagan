package com.yandex.daggerlite.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.ksp.lang.KspModelFactoryImpl
import com.yandex.daggerlite.ksp.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.ksp.lang.Utils
import com.yandex.daggerlite.process.Logger
import com.yandex.daggerlite.process.ProcessorDelegate
import com.yandex.daggerlite.process.process
import java.io.Writer

internal class KspDaggerLiteProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor, ProcessorDelegate<KSClassDeclaration> {
    override val langModelFactory by lazy(::KspModelFactoryImpl)
    override val logger: Logger = KspLogger(environment.logger)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        Utils.init(resolver).use {
            process(
                sources = resolver.getSymbolsWithAnnotation(Component::class.java.canonicalName)
                    .filterIsInstance<KSClassDeclaration>(),
                delegate = this,
            )
            return emptyList()
        }
    }

    override fun createDeclaration(source: KSClassDeclaration) = TypeDeclarationLangModel(source)

    override fun openFileForGenerating(
        source: KSClassDeclaration,
        packageName: String,
        className: String,
    ): Writer {
        val sourceFile = source.containingFile
        return environment.codeGenerator.createNewFile(
            Dependencies(
                aggregating = false,
                *(sourceFile?.let { arrayOf(sourceFile) } ?: emptyArray()),
            ),
            packageName = packageName,
            fileName = className,
            extensionName = "java",
        ).bufferedWriter()
    }
}

