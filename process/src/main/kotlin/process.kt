package com.yandex.daggerlite.process

import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Error
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Warning
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.validate

fun <Source> process(
    sources: Sequence<Source>,
    delegate: ProcessorDelegate<Source>,
) {
    ObjectCacheRegistry.use {
        val graphRoots = sources.mapNotNull { source ->
            ComponentModel(delegate.createDeclaration(source)).takeIf { it.isRoot }
        }.map { model ->
            BindingGraph(root = model)
        }.sortedBy {
            // To ensure stable aggregated error messages
            it.model.type.declaration.qualifiedName
        }.toList()

        val logger = LoggerDecorator(delegate.logger)

        val validationResults = validate(graphRoots)

        validationResults.forEach { locatedMessage ->
            val message = Strings.formatMessage(
                message = locatedMessage.message.contents,
                encounterPaths = locatedMessage.encounterPaths,
                notes = locatedMessage.message.notes
            )
            when (locatedMessage.message.kind) {
                Error -> logger.error(message)
                Warning -> logger.warning(message)
            }
        }

        if (validationResults.any { it.message.kind == Error }) {
            return
        }

        for (source in sources) {
            try {
                val model = ComponentModel(delegate.createDeclaration(source))
                if (!model.isRoot) {
                    continue
                }

                val graph = BindingGraph(
                    root = model,
                )

                ComponentGeneratorFacade(
                    graph = graph,
                ).use { generator ->
                    delegate.openFileForGenerating(
                        source = source,
                        packageName = generator.targetPackageName,
                        className = generator.targetClassName,
                    ).use(generator::generateTo)
                }

            } catch (e: Throwable) {
                logger.error(buildString {
                    appendLine("Internal Processor Error while processing $source")
                    appendLine("Please, report this to the maintainers, along with the code (diff) that triggered this.")
                    appendLine(e.message)
                    appendLine(e.stackTraceToString())
                })
            }
        }
    }
}