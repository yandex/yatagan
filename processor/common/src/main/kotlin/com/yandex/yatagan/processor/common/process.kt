/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.processor.common

import com.yandex.yatagan.base.api.childrenSequence
import com.yandex.yatagan.base.loadServices
import com.yandex.yatagan.codegen.impl.ComponentGeneratorFacade
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.impl.BindingGraph
import com.yandex.yatagan.core.graph.impl.Options as GraphOptions
import com.yandex.yatagan.core.graph.impl.ThreadChecker
import com.yandex.yatagan.core.model.impl.ComponentModel
import com.yandex.yatagan.lang.common.LangOptions
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.validation.LocatedMessage
import com.yandex.yatagan.validation.ValidationMessage.Kind.Error
import com.yandex.yatagan.validation.ValidationMessage.Kind.MandatoryWarning
import com.yandex.yatagan.validation.ValidationMessage.Kind.Warning
import com.yandex.yatagan.validation.format.format
import com.yandex.yatagan.validation.impl.GraphValidationExtension
import com.yandex.yatagan.validation.impl.validate
import com.yandex.yatagan.validation.spi.ValidationPluginProvider

/**
 * Main processor routine. Takes [sources] and processes them using [delegate].
 */
fun <Source> process(
    sources: Sequence<Source>,
    delegate: ProcessorDelegate<Source>,
) {
    run {
        val rootModels = sources.mapNotNull { source ->
            ComponentModel(delegate.createDeclaration(source))
        }.filter { it.isRoot }.toList()

        if (rootModels.isEmpty()) {
            return
        }

        val logger = LoggerDecorator(delegate.logger)
        val plugins = loadServices<ValidationPluginProvider>()

        val threadChecker = ThreadChecker(
            delegate.lexicalScope,
            delegate.options[StringOption.ThreadCheckerClassName],
        )
        if (!reportMessages(
                logger = logger,
                options = delegate.options,
                messages = validate(threadChecker),
            )
        ) {
            return
        }

        for (rootModel in rootModels) {
            val graphRoot = BindingGraph(
                root = rootModel,
            )
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

            if (!reportMessages(
                    logger = logger,
                    options = delegate.options,
                    messages = allMessages,
                )
            ) {
                // If the graph is not valid, bail out
                continue
            }

            try {
                val generator = ComponentGeneratorFacade(
                    graph = graphRoot,
                    maxSlotsPerSwitch = delegate.options[IntOption.MaxSlotsPerSwitch],
                    enableProvisionNullChecks = !delegate.options[BooleanOption.OmitProvisionNullChecks],
                    sortMethodsForTesting = delegate.options[BooleanOption.SortMethodsForTesting],
                    enableDaggerCompatMode = delegate.options[BooleanOption.DaggerCompatibilityMode],
                    threadChecker = threadChecker,
                )
                generator.generate().forEach { generated ->
                    delegate.openFileForGenerating(
                        sources = allSourcesSequence(delegate, graphRoot),
                        packageName = generated.targetPackageName,
                        className = generated.targetClassName,
                    ).use(generated::generateTo)
                }
            } catch (e: Throwable) {
                logger.reportInternalProcessorError(
                    target = graphRoot.toString(null).toString(),
                    error = e,
                )
            }
        }
    }
}

fun reportMessages(
    logger: Logger,
    options: Options,
    messages: Collection<LocatedMessage>,
) : Boolean {
    val usePlainOutput = options[BooleanOption.UsePlainOutput]
    val strictMode = options[BooleanOption.StrictMode]
    var hasErrors = false
    messages.forEach { locatedMessage ->
        val message = locatedMessage.format(
            maxEncounterPaths = options[IntOption.MaxIssueEncounterPaths],
        ).run {
            if (usePlainOutput) toString() else toAnsiEscapedString()
        }
        when (locatedMessage.message.kind) {
            Error -> logger.error(message).also { hasErrors = true }
            MandatoryWarning -> if (strictMode) {
                hasErrors = true
                logger.error(message)
            } else {
                logger.warning(message)
            }

            Warning -> logger.warning(message)
        }
    }
    return !hasErrors
}

fun Logger.reportInternalProcessorError(target: String, error: Throwable) {
    error(buildString {
        appendLine("Internal Processor Error while processing $target")
        appendLine("Please, report this via https://github.com/yandex/yatagan/issues/new, " +
                "preferably with the sample code/project that reproduces this.")
        appendLine(error.message)
        appendLine(error.stackTraceToString())
    })
}

/**
 * A hook to be called by processor once per [LexicalScope].
 */
fun initScopedOptions(
    delegate: ProcessorDelegate<*>,
) = with(delegate.lexicalScope) {
    ext[GraphOptions] = GraphOptions(
        allConditionsLazy = delegate.options[BooleanOption.AllConditionsLazy],
    )
    ext[LangOptions] = LangOptions(
        daggerCompatibilityMode = delegate.options[BooleanOption.DaggerCompatibilityMode],
    )
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