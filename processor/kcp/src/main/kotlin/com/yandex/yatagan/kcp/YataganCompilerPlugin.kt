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
import org.jetbrains.kotlin.backend.jvm.ir.getIoFile
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
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
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.Position
import org.jetbrains.kotlin.incremental.components.ScopeKind
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
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
            // Present only in incremental builds; null makes IC recording a no-op.
            lookupTracker = configuration.get(CommonConfigurationKeys.LOOKUP_TRACKER),
            expectActualTracker = configuration.get(CommonConfigurationKeys.EXPECT_ACTUAL_TRACKER),
        ))
    }
}

private class YataganIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val processorOptions: Map<String, String>,
    private val lookupTracker: LookupTracker?,
    private val expectActualTracker: ExpectActualTracker?,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val extensionStart = System.nanoTime()
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

            var fileHasRootComponent = false
            for (component in components) {
                when (val result = validation.validateComponent(scope, component)) {
                    KcpGraphValidation.ComponentResult.NotRoot -> Unit
                    KcpGraphValidation.ComponentResult.Invalid -> {
                        fileHasRootComponent = true
                        canGenerate = false
                    }
                    is KcpGraphValidation.ComponentResult.Valid -> {
                        fileHasRootComponent = true
                        if (codegenEnabled) {
                            generationCandidates += GenerationCandidate(
                                file = file,
                                component = component,
                                graph = result.graph,
                                threadChecker = validation.createThreadChecker(scope),
                            )
                        }
                    }
                }
            }
            if (fileHasRootComponent) {
                recordIncrementalDependencies(file, scope)
            }
        }

        if (!canGenerate || !codegenEnabled) return
        val generationFailures = AtomicIrGenerationCoordinator(
            candidates = generationCandidates,
            prepare = { candidate ->
                val start = System.nanoTime()
                val emitter = IrComponentEmitter(
                    pluginContext = pluginContext,
                    targetFile = candidate.file,
                    graph = candidate.graph,
                    options = IrComponentEmitter.Options(
                        enableProvisionNullChecks = validation.enableProvisionNullChecks,
                        threadChecker = candidate.threadChecker,
                    ),
                )
                emitter.prepare().also {
                    messageCollector.report(
                        CompilerMessageSeverity.LOGGING,
                        "[yatagan] timing emit-prepare ${candidate.component.name}: " +
                                "${(System.nanoTime() - start) / 1_000_000}ms",
                    )
                }
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
        messageCollector.report(
            CompilerMessageSeverity.LOGGING,
            "[yatagan] timing extension total (${moduleFragment.name}): " +
                    "${(System.nanoTime() - extensionStart) / 1_000_000}ms",
        )
    }

    /**
     * Tells incremental compilation that this root component file depends on every class its
     * graphs read. The generated implementation lives in the component's own file, yet the file's
     * source references almost none of the graph closure — without these records, editing a
     * binding elsewhere never re-triggers generation and the compiled component goes stale.
     *
     * Member-name lookups catch changes to declarations that exist now; the expect/actual
     * file link additionally covers declarations *added* to a same-module class later (there is
     * no name to look up in advance). Cross-module changes flow through classpath ABI diffing
     * against the recorded lookups.
     */
    private fun recordIncrementalDependencies(file: IrFile, scope: KcpLexicalScope) {
        if (lookupTracker == null && expectActualTracker == null) return
        val start = System.nanoTime()
        var lookups = 0
        val rootIoFile = file.getIoFile()
        val rootPath = rootIoFile?.path ?: file.fileEntry.name
        for (clazz in scope.resolvedClasses) {
            if (clazz.fileOrNull == file) continue
            val classId = clazz.classId ?: continue
            if (lookupTracker != null) {
                lookupTracker.record(
                    filePath = rootPath,
                    position = Position.NO_POSITION,
                    scopeFqName = (classId.outerClassId?.asSingleFqName() ?: classId.packageFqName).asString(),
                    scopeKind = ScopeKind.PACKAGE,
                    name = classId.shortClassName.asString(),
                )
                // The graph reads the whole member surface (annotations included), so any member
                // change may change the graph.
                val classFqName = clazz.kotlinFqName.asString()
                for (declaration in clazz.declarations) {
                    val name = (declaration as? IrDeclarationWithName)?.name ?: continue
                    if (name.isSpecial) continue
                    lookupTracker.record(
                        filePath = rootPath,
                        position = Position.NO_POSITION,
                        scopeFqName = classFqName,
                        scopeKind = ScopeKind.CLASSIFIER,
                        name = name.asString(),
                    )
                    lookups++
                }
            }
            if (expectActualTracker != null && rootIoFile != null) {
                val classFile = clazz.fileOrNull?.getIoFile()
                if (classFile != null && classFile != rootIoFile && classFile.isAbsolute) {
                    expectActualTracker.report(expectedFile = classFile, actualFile = rootIoFile)
                }
            }
        }
        messageCollector.report(
            org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING,
            "[yatagan] timing icRecord ${file.fileEntry.name.substringAfterLast('/')}: " +
                    "${(System.nanoTime() - start) / 1_000_000}ms " +
                    "(${scope.resolvedClasses.size} classes, $lookups member lookups)",
        )
    }

    private data class GenerationCandidate(
        val file: IrFile,
        val component: IrClass,
        val graph: BindingGraph,
        val threadChecker: com.yandex.yatagan.core.graph.ThreadChecker,
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
