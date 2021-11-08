package com.yandex.daggerlite.process

import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.BindingGraph
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade

fun <Source> process(
    sources: Sequence<Source>,
    delegate: ProcessorDelegate<Source>,
) {
    ObjectCacheRegistry.use {
        for (source in sources) {
            try {
                val model = ComponentModel(delegate.createDeclaration(source))
                if (!model.isRoot) {
                    continue
                }

                val graph = BindingGraph(
                    root = model,
                    modelFactory = delegate.langModelFactory,
                )

                if (graph.missingBindings.isNotEmpty()) {
                    graph.missingBindings.forEach { node ->
                        delegate.logger.error("Missing binding for $node in $graph")
                    }
                    continue
                }

                val generator = ComponentGeneratorFacade(
                    graph = graph,
                )

                delegate.openFileForGenerating(
                    source = source,
                    packageName = generator.targetPackageName,
                    className = generator.targetClassName,
                ).use(generator::generateTo)

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