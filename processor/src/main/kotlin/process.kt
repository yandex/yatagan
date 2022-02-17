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

/**
 * Main processor routine. Takes [sources] and processes them using [delegate].
 */
fun <Source> process(
    sources: Sequence<Source>,
    delegate: ProcessorDelegate<Source>,
) {
    ObjectCacheRegistry.use {
        val graphRoots = sources.mapNotNull { source ->
            val model = ComponentModel(delegate.createDeclaration(source))
            if (model.isRoot) {
                BindingGraph(
                        root = model,
                )
            } else null
        }.toList()

        val validationPluginProviders = loadServices<ValidationPluginProvider>()
        val toValidate: List<MayBeInvalid> = buildList {
            // Sorting is used to ensure stable aggregated error messages
            for (graphRoot: BindingGraph in graphRoots.sortedBy { it.model.type }) {
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

        for (graph in graphRoots) {
            try {
                ComponentGeneratorFacade(
                    graph = graph,
                ).use { generator ->
                    // Cast relies on the fact that `HasPlatformModel.platformModel` is the Source type.
                    @Suppress("UNCHECKED_CAST")
                    delegate.openFileForGenerating(
                        sources = sequence {
                            suspend fun SequenceScope<Any?>.forGraph(graph: BindingGraph) {
                                yield(graph.model.type.declaration.platformModel)
                                for (module in graph.modules) {
                                    yield(module.type.declaration.platformModel)
                                }
                                for (child in graph.children) {
                                    forGraph(child)
                                }
                            }
                            forGraph(graph)
                        } as Sequence<Source>,
                        packageName = generator.targetPackageName,
                        className = generator.targetClassName,
                    ).use(generator::generateTo)
                }

            } catch (e: Throwable) {
                logger.error(buildString {
                    appendLine("Internal Processor Error while processing $graph")
                    appendLine("Please, report this to the maintainers, along with the code (diff) that triggered this.")
                    appendLine(e.message)
                    appendLine(e.stackTraceToString())
                })
            }
        }
    }
}
