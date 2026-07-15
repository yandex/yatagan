/*
 * Copyright 2026 Yandex LLC
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

package com.yandex.yatagan.kcp

import com.yandex.yatagan.core.graph.BindingGraph as BindingGraphModel
import com.yandex.yatagan.core.graph.impl.BindingGraph
import com.yandex.yatagan.core.graph.impl.Options as GraphOptions
import com.yandex.yatagan.core.graph.impl.ThreadChecker
import com.yandex.yatagan.core.model.impl.ComponentModel
import com.yandex.yatagan.lang.kcp.KcpLexicalScope
import com.yandex.yatagan.processor.common.BooleanOption
import com.yandex.yatagan.processor.common.IntOption
import com.yandex.yatagan.processor.common.Logger
import com.yandex.yatagan.processor.common.LoggerDecorator
import com.yandex.yatagan.processor.common.Options as ProcessorOptions
import com.yandex.yatagan.processor.common.StringOption
import com.yandex.yatagan.validation.LocatedMessage
import com.yandex.yatagan.validation.ValidationMessage.Kind.Error
import com.yandex.yatagan.validation.ValidationMessage.Kind.MandatoryWarning
import com.yandex.yatagan.validation.ValidationMessage.Kind.Warning
import com.yandex.yatagan.validation.format.format
import com.yandex.yatagan.validation.impl.validate
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass

internal class KcpGraphValidation(
    rawOptions: Map<String, String>,
    messageCollector: MessageCollector,
) {
    private val options = ProcessorOptions(rawOptions)
    private val logger = LoggerDecorator(object : Logger {
        override fun error(message: String) {
            messageCollector.report(CompilerMessageSeverity.ERROR, message)
        }

        override fun warning(message: String) {
            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, message)
        }
    })

    fun initialize(scope: KcpLexicalScope) {
        scope.ext[GraphOptions] = GraphOptions(
            allConditionsLazy = options[BooleanOption.AllConditionsLazy],
        )
    }

    fun validateThreadChecker(scope: KcpLexicalScope): Boolean {
        val threadChecker = ThreadChecker(
            lexicalScope = scope,
            threadCheckerClassName = options[StringOption.ThreadCheckerClassName],
        )
        return report(validate(threadChecker))
    }

    fun validateComponent(
        scope: KcpLexicalScope,
        component: IrClass,
    ): ComponentResult {
        var processingTarget = component.name.asString()
        return try {
            val model = ComponentModel(scope.getTypeDeclaration(component))
            if (!model.isRoot) {
                return ComponentResult.NotRoot
            }

            val graph = BindingGraph(root = model)
            processingTarget = graph.toString(null).toString()
            if (report(validate(graph))) {
                ComponentResult.Valid(graph)
            } else {
                ComponentResult.Invalid
            }
        } catch (e: Throwable) {
            reportInternalProcessorError(processingTarget, e)
            ComponentResult.Invalid
        }
    }

    fun reportUnsupportedComponent(component: IrClass, reason: String) {
        logger.error("Unsupported KCP component ${component.name}: $reason")
    }

    fun reportInternalProcessorError(processingTarget: String, error: Throwable) {
        logger.error(buildString {
            appendLine("Internal Processor Error while processing $processingTarget")
            appendLine("Please, report this via https://github.com/yandex/yatagan/issues/new, " +
                    "preferably with the sample code/project that reproduces this.")
            appendLine(error.message)
            appendLine(error.stackTraceToString())
        })
    }

    private fun report(messages: Collection<LocatedMessage>): Boolean {
        val usePlainOutput = options[BooleanOption.UsePlainOutput]
        val strictMode = options[BooleanOption.StrictMode]
        var hasErrors = false

        for (locatedMessage in messages) {
            val message = locatedMessage.format(
                maxEncounterPaths = options[IntOption.MaxIssueEncounterPaths],
            ).run {
                if (usePlainOutput) toString() else toAnsiEscapedString()
            }
            when (locatedMessage.message.kind) {
                Error -> {
                    hasErrors = true
                    logger.error(message)
                }
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

    sealed interface ComponentResult {
        data object NotRoot : ComponentResult
        data object Invalid : ComponentResult
        data class Valid(
            val graph: BindingGraphModel,
        ) : ComponentResult
    }
}
