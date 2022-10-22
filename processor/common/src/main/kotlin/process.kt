package com.yandex.daggerlite.process

import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.base.loadServices
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.childrenSequence
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.spi.ValidationPluginProvider
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Error
import com.yandex.daggerlite.validation.ValidationMessage.Kind.MandatoryWarning
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Warning
import com.yandex.daggerlite.validation.format.format
import com.yandex.daggerlite.validation.impl.GraphValidationExtension
import com.yandex.daggerlite.validation.impl.validate

/**
 * Main processor routine. Takes [sources] and processes them using [delegate].
 */
fun <Source> process(
    sources: Sequence<Source>,
    delegate: ProcessorDelegate<Source>,
) {
    val usePlainOutput = delegate.options[Options.UsePlainOutput]
    val strictMode = delegate.options[Options.StrictMode]
    ObjectCacheRegistry.use {
        val rootModels = sources.mapNotNull { source ->
            ComponentModel(delegate.createDeclaration(source))
        }.filter { it.isRoot }.toList()

        val logger = LoggerDecorator(delegate.logger)
        val plugins = loadServices<ValidationPluginProvider>()
        for (rootModel in rootModels) {
            val graphRoot = BindingGraph(root = rootModel)
            val allMessages = buildList {
                addAll(validate(graphRoot))
                if (plugins.isNotEmpty()) {
                    val extension = GraphValidationExtension(
                        graph = graphRoot,
                        validationPluginProviders = plugins,
                    )
                    addAll(validate(extension))
                }
            }

            allMessages.forEach { locatedMessage ->
                val message = locatedMessage.format(
                    maxEncounterPaths = delegate.options[Options.MaxIssueEncounterPaths],
                ).run {
                    if (usePlainOutput) toString() else toAnsiEscapedString()
                }
                when (locatedMessage.message.kind) {
                    Error -> logger.error(message)
                    MandatoryWarning -> if (strictMode) {
                        logger.error(message)
                    } else {
                        logger.warning(message)
                    }

                    Warning -> logger.warning(message)
                }
            }
            if (allMessages.any { it.message.kind == Error }) {
                // If the graph is not valid, bail out
                continue
            }

            try {
                val generator = ComponentGeneratorFacade(
                    graph = graphRoot,
                )
                delegate.openFileForGenerating(
                    sources = allSourcesSequence(delegate, graphRoot),
                    packageName = generator.targetPackageName,
                    className = generator.targetClassName,
                ).use(generator::generateTo)
            } catch (e: Throwable) {
                logger.error(buildString {
                    appendLine("Internal Processor Error while processing $graphRoot")
                    appendLine("Please, report this to the maintainers, along with the code (diff) that triggered this.")
                    appendLine(e.message)
                    appendLine(e.stackTraceToString())
                })
            }
        }
    }
}

private fun <Source> allSourcesSequence(
    delegate: ProcessorDelegate<Source>,
    graphRoot: BindingGraph,
) = graphRoot.childrenSequence(includeThis = true).flatMap { graph ->
    sequenceOf(
        delegate.getSourceFor(graph.model.type.declaration),
    ) + graph.modules.asSequence().map { module ->
        delegate.getSourceFor(module.type.declaration)
    }
}