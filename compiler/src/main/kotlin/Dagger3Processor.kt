package com.yandex.daggerlite.compiler

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.generator.GenerationLogger

internal class Dagger3Processor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val logger = KspGenerationLogger(environment.logger)

        resolver.getSymbolsWithAnnotation(AnnotationNames.Component)
            .filterIsInstance<KSClassDeclaration>()
            .map { KspComponentModel(it) }
            .filter { it.isRoot }
            .forEach { model ->
                val graph = BindingGraph(
                    root = model,
                )
                if (graph.missingBindings.isNotEmpty()) {
                    graph.missingBindings.forEach { node ->
                        logger.error("Missing binding for $node")
                    }
                    return@forEach
                }
                val generator = ComponentGeneratorFacade(
                    graph = graph,
                )
                environment.codeGenerator.createNewFile(
                    Dependencies(
                        aggregating = false,
                        // fixme: provide proper dependencies here.
                    ),
                    packageName = generator.targetPackageName,
                    fileName = generator.targetClassName,
                    extensionName = generator.targetLanguage.extension,
                ).use { file ->
                    file.bufferedWriter().use { writer ->
                        generator.generateTo(
                            out = writer,
                        )
                    }
                }
            }
        return emptyList()
    }
}

internal class KspGenerationLogger(
    private val logger: KSPLogger,
) : GenerationLogger {
    override fun error(message: String) {
        logger.error(message /*TODO: support where*/)
    }

    override fun warning(message: String) {
        logger.warn(message /*TODO: support where*/)
    }
}