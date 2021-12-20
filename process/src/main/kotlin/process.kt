package com.yandex.daggerlite.process

import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Error
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Note
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Warning
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
        }.toList()

        val validationResults = validate(graphRoots)

        validationResults.forEach { locatedMessage ->
            val message = buildString {
                appendLine(locatedMessage.message)
                appendLine("\tEncountered in:")
                locatedMessage.encounterPaths.forEach { path ->
                    append('\t')
                    path.joinTo(this, separator = " -> ")
                    appendLine()
                }
            }
            when (locatedMessage.message.kind) {
                Error -> delegate.logger.error(message)
                Warning -> delegate.logger.warning(message)
                Note -> TODO()
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

            } catch (e: Exception) {
                delegate.logger.error(buildString {
                    appendLine("While processing $source")
                    appendLine(e.message)
                    appendLine(e.stackTraceToString())
                })
            }
        }
    }
}