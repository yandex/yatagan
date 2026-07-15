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

@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.kcp

import com.yandex.yatagan.codegen.ir.IrComponentEmitter
import com.yandex.yatagan.codegen.ir.UnsupportedIrGraphException
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.lang.kcp.KcpLexicalScope
import com.yandex.yatagan.processor.common.Options
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName

private const val PLUGIN_ID = "com.yandex.yatagan"
private const val KCP_CODEGEN_OPTION = "yatagan.kcp.codegen"
private val KcpCodegenCliOption = CliOption(
    optionName = KCP_CODEGEN_OPTION,
    valueDescription = "<true|false>",
    description = "Whether the KCP proof-of-concept emits component implementations",
    required = false,
    allowMultipleOccurrences = false,
)
private val SharedProcessorOptions = Options.all()
private val ComponentFqName = FqName("com.yandex.yatagan.Component")
private val ProcessorOptionsKey = CompilerConfigurationKey.create<Map<String, String>>(
    "Yatagan processor options",
)

@OptIn(ExperimentalCompilerApi::class)
public class YataganCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = SharedProcessorOptions.map { option ->
        CliOption(
            optionName = option.key,
            valueDescription = "<value>",
            description = "Yatagan processor option ${option.key}",
            required = false,
            allowMultipleOccurrences = false,
        )
    } + KcpCodegenCliOption

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            KCP_CODEGEN_OPTION -> parseKcpCodegenOption(value)
            else -> {
                val sharedOption = SharedProcessorOptions.singleOrNull {
                    it.key == option.optionName
                } ?: throw CliOptionProcessingException("Unknown option: ${option.optionName}")
                try {
                    sharedOption.parse(value)
                } catch (e: RuntimeException) {
                    throw CliOptionProcessingException(e.message ?: "Invalid option value: $value", e)
                }
            }
        }
        configuration.put(
            ProcessorOptionsKey,
            configuration.get(ProcessorOptionsKey, emptyMap()) + (option.optionName to value),
        )
    }
}

@OptIn(ExperimentalCompilerApi::class)
public class YataganCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(YataganIrGenerationExtension(
            messageCollector = configuration.get(
                CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                MessageCollector.NONE,
            ),
            processorOptions = configuration.get(ProcessorOptionsKey, emptyMap()),
        ))
    }
}

private class YataganIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val processorOptions: Map<String, String>,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val validation = KcpGraphValidation(
            rawOptions = processorOptions,
            messageCollector = messageCollector,
        )
        val codegenEnabled = processorOptions[KCP_CODEGEN_OPTION]
            ?.let(::parseKcpCodegenOption)
            ?: true
        val generationCandidates = mutableListOf<GenerationCandidate>()
        var canGenerate = true
        var threadCheckerValidated = false

        for (file in moduleFragment.files) {
            val components = file.classesRecursively()
                .filter { it.hasAnnotation(ComponentFqName) }
                .toList()
            if (components.isEmpty()) continue

            val scope = KcpLexicalScope(pluginContext, file)
            validation.initialize(scope)
            if (!threadCheckerValidated) {
                threadCheckerValidated = true
                if (!validation.validateThreadChecker(scope)) {
                    return
                }
            }

            for (component in components) {
                when (val result = validation.validateComponent(scope, component)) {
                    KcpGraphValidation.ComponentResult.NotRoot -> Unit
                    KcpGraphValidation.ComponentResult.Invalid -> canGenerate = false
                    is KcpGraphValidation.ComponentResult.Valid -> {
                        if (codegenEnabled) {
                            generationCandidates += GenerationCandidate(
                                file = file,
                                component = component,
                                graph = result.graph,
                            )
                        }
                    }
                }
            }
        }

        if (!canGenerate || !codegenEnabled) return
        val generationFailures = AtomicIrGenerationCoordinator(
            candidates = generationCandidates,
            prepare = { candidate ->
                val emitter = IrComponentEmitter(
                    pluginContext = pluginContext,
                    targetFile = candidate.file,
                    graph = candidate.graph,
                )
                emitter.prepare()
            },
            emit = { _, prepared -> prepared.emit() },
            rollback = { candidate, implementation ->
                candidate.file.declarations.remove(implementation)
            },
        ).execute()
        for ((candidate, error) in generationFailures) {
            when (error) {
                is UnsupportedIrGraphException -> {
                    validation.reportUnsupportedComponent(
                        component = candidate.component,
                        reason = error.message ?: "unsupported binding graph",
                    )
                }
                else -> {
                    validation.reportInternalProcessorError(
                        candidate.component.name.asString(),
                        error,
                    )
                }
            }
        }
    }

    private data class GenerationCandidate(
        val file: IrFile,
        val component: IrClass,
        val graph: BindingGraph,
    )
}

private fun parseKcpCodegenOption(rawValue: String): Boolean = when (rawValue.lowercase()) {
    "yes", "enabled", "enable", "true" -> true
    "no", "disabled", "disable", "false" -> false
    else -> throw CliOptionProcessingException("Invalid boolean option value: $rawValue")
}

private fun IrDeclarationContainer.classesRecursively(): Sequence<IrClass> =
    declarations.asSequence()
        .filterIsInstance<IrClass>()
        .flatMap { sequenceOf(it) + it.classesRecursively() }
