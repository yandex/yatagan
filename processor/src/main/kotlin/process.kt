package com.yandex.daggerlite.process

import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.base.loadServices
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.spi.ValidationPluginProvider
import com.yandex.daggerlite.spi.impl.GraphValidationExtension
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Error
import com.yandex.daggerlite.validation.ValidationMessage.Kind.Warning
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.validate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Main processor routine. Takes [sources] and processes them using [delegate].
 */
fun <Source> process(
    sources: Sequence<Source>,
    delegate: ProcessorDelegate<Source>,
    useParallelProcessing: Boolean,
) {
    ObjectCacheRegistry.use {
        val dispatcher = if (useParallelProcessing) Dispatchers.Default else Dispatchers.Unconfined
        runBlocking(dispatcher) {
            val rootModels = sources.mapNotNull { source ->
                ComponentModel(delegate.createDeclaration(source))
            }.filter { it.isRoot }.toList()

            val logger = LoggerDecorator(delegate.logger)

            val pluginsDeferred: Deferred<List<ValidationPluginProvider>> = async {
                loadServices()
            }

            val supervisorJob = SupervisorJob()
            for (rootModel in rootModels) {
                val graphSupervisor = SupervisorJob(supervisorJob)
                launch(graphSupervisor) {
                    val graphRootDeferred = async {
                        BindingGraph(root = rootModel)
                    }
                    val baseValidationJob = async {
                        validate(graphRootDeferred.await())
                    }
                    val pluginsValidationJob = async {
                        val plugins = pluginsDeferred.await()
                        if (plugins.isEmpty()) {
                            emptyList()
                        } else {
                            val extension = GraphValidationExtension(
                                graph = graphRootDeferred.await(),
                                validationPluginProviders = plugins,
                            )
                            validate(extension)
                        }
                    }

                    val isValid = async {
                        val allMessages = baseValidationJob.await() + pluginsValidationJob.await()
                        allMessages.forEach { locatedMessage ->
                            val message = Strings.formatMessage(
                                message = locatedMessage.message.contents,
                                color = when (locatedMessage.message.kind) {
                                    Error -> Strings.StringColor.Red
                                    Warning -> Strings.StringColor.Yellow
                                },
                                encounterPaths = locatedMessage.encounterPaths,
                                notes = locatedMessage.message.notes
                            )
                            when (locatedMessage.message.kind) {
                                Error -> logger.error(message)
                                Warning -> logger.warning(message)
                            }
                        }
                        if (allMessages.any { it.message.kind == Error }) {
                            // If the graph is not valid, cancel this graph job
                            graphSupervisor.cancelAndJoin()
                        }
                    }

                    if (!useParallelProcessing) {
                        // Ensure validation is complete before codegen starts
                        isValid.await()
                    }

                    val graphRoot = graphRootDeferred.await()
                    try {
                        val codegenFacade = ComponentGeneratorFacade(
                            graph = graphRoot,
                        )
                        // Launch codegen
                        codegenFacade.use { generator ->
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
                                    forGraph(graphRoot)
                                } as Sequence<Source>,
                                packageName = generator.targetPackageName,
                                className = generator.targetClassName,
                            ).use(generator::generateTo)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        isValid.await()  // May throw cancellation in case of any validation errors

                        // If the execution got here, something is fishy here, likely a bug/internal error.
                        logger.error(buildString {
                            appendLine("Internal Processor Error while processing $graphRoot")
                            appendLine("Please, report this to the maintainers, along with the code (diff) that triggered this.")
                            appendLine(e.message)
                            appendLine(e.stackTraceToString())
                        })
                    }
                }
                graphSupervisor.complete()
            }
            supervisorJob.complete()
            supervisorJob.join()
        }
    }
}
