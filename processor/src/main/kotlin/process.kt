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
import kotlinx.coroutines.CoroutineExceptionHandler
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

            val internalErrorHandler = CoroutineExceptionHandler { _, throwable ->
                logger.error(buildString {
                    appendLine("Internal Processor Error")
                    appendLine("Please, report this to the maintainers, along with the code reproducing the issue.")
                    appendLine(throwable.message)
                    appendLine(throwable.stackTraceToString())
                })
            }

            val supervisorJob = SupervisorJob()
            for (rootModel in rootModels) {
                val graphSupervisor = SupervisorJob(supervisorJob)
                launch(graphSupervisor + internalErrorHandler) {
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

                    // Ensure validation is complete before codegen starts
                    isValid.await()

                    // TODO(DAGGERLITE-50): Try to support parallel codegen without parallel building and validation
                    val graphRoot = graphRootDeferred.await()
                    try {
                        val generator = ComponentGeneratorFacade(
                            graph = graphRoot,
                        )
                        // Launch codegen
                        delegate.openFileForGenerating(
                            sources = allSourcesSequence(delegate, graphRoot),
                            packageName = generator.targetPackageName,
                            className = generator.targetClassName,
                        ).use(generator::generateTo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        isValid.await()  // May throw cancellation in case of any validation errors
                        throw e  // If the execution got here, something is fishy here, likely a bug/internal error.
                    }
                }
                graphSupervisor.complete()
            }
            supervisorJob.complete()
            supervisorJob.join()
        }
    }
}

private fun <Source> allSourcesSequence(
    delegate: ProcessorDelegate<Source>,
    graphRoot: BindingGraph
) = sequence {
    suspend fun SequenceScope<Source>.forGraph(graph: BindingGraph) {
        yield(delegate.getSourceFor(graph.model.type.declaration))
        for (module in graph.modules) {
            yield(delegate.getSourceFor(module.type.declaration))
        }
        for (child in graph.children) {
            forGraph(child)
        }
    }
    forGraph(graphRoot)
}
