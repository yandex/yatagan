package com.yandex.daggerlite.process

import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.BindingGraph.NodeRequester.BindingRequester
import com.yandex.daggerlite.graph.BindingGraph.NodeRequester.EntryPointRequester
import com.yandex.daggerlite.graph.BindingGraph.NodeRequester.MemberInjectRequester
import com.yandex.daggerlite.graph.impl.BindingGraph

fun <Source> process(
    sources: Sequence<Source>,
    delegate: ProcessorDelegate<Source>,
) {
    fun reportMissingBindings(graph: BindingGraph): Boolean {
        var hasMissing = false
        if (graph.missingBindings.isNotEmpty()) {
            graph.missingBindings.forEach { (node, requesters) ->
                delegate.logger.error(buildString {
                    appendLine("Missing binding for $node in $graph")
                    requesters.forEach {
                        val requestedDescription = when(it) {
                            is BindingRequester -> it.binding.toString()
                            is EntryPointRequester -> it.entryPoint.getter.let { func -> "${func.owner}.${func.name}" }
                            is MemberInjectRequester -> it.injector.injector.toString()
                        }
                        appendLine(" - Requested from here: $requestedDescription")
                    }
                })
                hasMissing = true
            }
        }
        for (child in graph.children) {
            hasMissing = hasMissing || reportMissingBindings(child)
        }
        return hasMissing
    }

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

                if (reportMissingBindings(graph)) {
                    continue
                }

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