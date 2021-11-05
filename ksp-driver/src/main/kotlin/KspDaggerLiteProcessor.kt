package com.yandex.daggerlite.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.BindingGraph
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.ksp.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.ksp.lang.Utils

internal class KspDaggerLiteProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        Utils.init(resolver).use {
            ObjectCacheRegistry.use {
                val logger = KspGenerationLogger(environment.logger)
                for (annotated in resolver.getSymbolsWithAnnotation(Component::class.java.canonicalName)) {
                    annotated as KSClassDeclaration
                    val model = ComponentModel(TypeDeclarationLangModel(annotated))
                    if (!model.isRoot) {
                        continue
                    }
                    val graph = BindingGraph(
                        root = model,
                    )
                    if (graph.missingBindings.isNotEmpty()) {
                        graph.missingBindings.forEach { node ->
                            logger.error("Missing binding for $node")
                        }
                        continue
                    }
                    val generator = ComponentGeneratorFacade(
                        graph = graph,
                    )
                    val sourceFile = annotated.containingFile
                    environment.codeGenerator.createNewFile(
                        Dependencies(
                            aggregating = false,
                            *(sourceFile?.let { arrayOf(sourceFile) } ?: emptyArray()),
                        ),
                        packageName = generator.targetPackageName,
                        fileName = generator.targetClassName,
                        extensionName = "java",
                    ).bufferedWriter().use { writer ->
                        generator.generateTo(
                            out = writer,
                        )
                    }
                }
            }
            return emptyList()
        }
    }
}

