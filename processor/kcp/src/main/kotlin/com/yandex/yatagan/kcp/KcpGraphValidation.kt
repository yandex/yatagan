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

import com.yandex.yatagan.base.loadServices
import com.yandex.yatagan.core.graph.BindingGraph as BindingGraphModel
import com.yandex.yatagan.core.graph.ThreadChecker as ThreadCheckerModel
import com.yandex.yatagan.core.graph.impl.BindingGraph
import com.yandex.yatagan.core.graph.impl.Options as GraphOptions
import com.yandex.yatagan.core.graph.impl.ThreadChecker
import com.yandex.yatagan.core.model.impl.ComponentModel
import com.yandex.yatagan.lang.kcp.KcpLexicalScope
import com.yandex.yatagan.processor.common.BooleanOption
import com.yandex.yatagan.processor.common.LoggerDecorator
import com.yandex.yatagan.processor.common.Options as ProcessorOptions
import com.yandex.yatagan.processor.common.StringOption
import com.yandex.yatagan.processor.common.reportInternalProcessorError
import com.yandex.yatagan.processor.common.reportMessages
import com.yandex.yatagan.validation.LocatedMessage
import com.yandex.yatagan.validation.impl.GraphValidationExtension
import com.yandex.yatagan.validation.impl.validate
import com.yandex.yatagan.validation.spi.ValidationPluginProvider
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass

internal class KcpGraphValidation(
    rawOptions: Map<String, String>,
    messageCollector: MessageCollector,
) {
    private val options = ProcessorOptions(rawOptions)

    // SPI validation plugins ride the compiler plugin classpath: kotlinc loads all -Xplugin jars
    // into one classloader, so ServiceLoader sees providers from jars placed next to this one
    // (e.g. via the `kotlinCompilerPluginClasspath` Gradle configuration).
    private val validationPluginProviders: List<ValidationPluginProvider> by lazy {
        loadServices<ValidationPluginProvider>()
    }
    private val logger = LoggerDecorator(KcpLogger(messageCollector))
    private var threadCheckerValid: Boolean? = null

    fun initialize(scope: KcpLexicalScope) {
        scope.ext[GraphOptions] = GraphOptions(
            allConditionsLazy = options[BooleanOption.AllConditionsLazy],
        )
    }

    val enableCodegen: Boolean
        get() = options[BooleanOption.KcpCodegen]

    val enableProvisionNullChecks: Boolean
        get() = !options[BooleanOption.OmitProvisionNullChecks]

    fun createThreadChecker(scope: KcpLexicalScope): ThreadCheckerModel =
        ThreadChecker(
            lexicalScope = scope,
            threadCheckerClassName = options[StringOption.ThreadCheckerClassName],
        )

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
            if (!isThreadCheckerValid(scope)) {
                return ComponentResult.Invalid
            }

            val graph = BindingGraph(root = model)
            processingTarget = graph.toString(null).toString()
            val coreMessages = validate(graph)
            val pluginMessages = if (validationPluginProviders.isNotEmpty()) {
                validate(GraphValidationExtension(
                    validationPluginProviders = validationPluginProviders,
                    graph = graph,
                ))
            } else emptyList()

            if (report(coreMessages + pluginMessages)) {
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
        logger.reportInternalProcessorError(target = processingTarget, error = error)
    }

    private fun isThreadCheckerValid(scope: KcpLexicalScope): Boolean {
        return threadCheckerValid ?: report(validate(createThreadChecker(scope))).also {
            threadCheckerValid = it
        }
    }

    private fun report(messages: Collection<LocatedMessage>): Boolean {
        return reportMessages(
            logger = logger,
            options = options,
            messages = messages,
        )
    }

    sealed interface ComponentResult {
        data object NotRoot : ComponentResult
        data object Invalid : ComponentResult
        data class Valid(
            val graph: BindingGraphModel,
        ) : ComponentResult
    }
}
