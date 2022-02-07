package com.yandex.daggerlite.process

import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.spi.ValidationPluginProvider
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Error
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Warning
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.validate

fun <Source> process(
    sources: Sequence<Source>,
    delegate: ProcessorDelegate<Source>,
) {
    ObjectCacheRegistry.use {
        val graphRoots = buildMap<BindingGraph, Source> {
            for (source in sources) {
                val model = ComponentModel(delegate.createDeclaration(source))
                if (!model.isRoot) {
                    continue
                }
                val graph = BindingGraph(
                    root = model,
                )
                put(graph, source)
            }
        }

        val validationPluginProviders = loadServices<ValidationPluginProvider>()
        val toValidate: List<MayBeInvalid> = buildList {
            // Sorting is used to ensure stable aggregated error messages
            for (graphRoot: BindingGraph in graphRoots.keys.sortedBy { it.model.type }) {
                // Add graph itself
                add(graphRoot)
                // Add graph extension
                if (validationPluginProviders.isNotEmpty()) {
                    add(GraphValidationExtension(
                        graph = graphRoot,
                        validationPluginProviders = validationPluginProviders,
                    ))
                }
            }
        }

        val validationResults = validate(toValidate)
        val logger = LoggerDecorator(delegate.logger)
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

        for ((graph, source) in graphRoots) {
            try {
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
