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
import com.yandex.yatagan.core.graph.ThreadChecker
import com.yandex.yatagan.lang.kcp.KcpLexicalScope
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName

private val ComponentFqName = FqName("com.yandex.yatagan.Component")

internal class YataganIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val processorOptions: Map<String, String>,
    private val incrementalDependencyRecorder: IncrementalDependencyRecorder,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val validation = KcpGraphValidation(
            rawOptions = processorOptions,
            messageCollector = messageCollector,
        )
        val generationCandidates = mutableListOf<GenerationCandidate>()
        var canGenerate = true

        for (file in moduleFragment.files) {
            val components = file.classesRecursively()
                .filter { it.hasAnnotation(ComponentFqName) }
                .toList()
            if (components.isEmpty()) continue

            val scope = KcpLexicalScope(pluginContext, file)
            validation.initialize(scope)

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
                        if (validation.enableCodegen) {
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
                incrementalDependencyRecorder.record(file, scope)
            }
        }

        if (!canGenerate || !validation.enableCodegen) return
        emitComponents(pluginContext, validation, generationCandidates)
    }

    /** Prepares every candidate before any of them is emitted. */
    private fun emitComponents(
        pluginContext: IrPluginContext,
        validation: KcpGraphValidation,
        candidates: List<GenerationCandidate>,
    ) {
        val prepared = mutableListOf<IrComponentEmitter.PreparedIrComponent>()
        var hasFailures = false
        for (candidate in candidates) {
            try {
                prepared += IrComponentEmitter(
                    pluginContext = pluginContext,
                    targetFile = candidate.file,
                    graph = candidate.graph,
                    options = IrComponentEmitter.Options(
                        enableProvisionNullChecks = validation.enableProvisionNullChecks,
                        threadChecker = candidate.threadChecker,
                    ),
                ).prepare()
            } catch (e: UnsupportedIrGraphException) {
                hasFailures = true
                validation.reportUnsupportedComponent(
                    component = candidate.component,
                    reason = e.message ?: "unsupported binding graph",
                )
            } catch (e: Throwable) {
                hasFailures = true
                validation.reportInternalProcessorError(candidate.component.name.asString(), e)
            }
        }
        if (hasFailures) return
        prepared.forEach { it.emit() }
    }

    private data class GenerationCandidate(
        val file: IrFile,
        val component: IrClass,
        val graph: BindingGraph,
        val threadChecker: ThreadChecker,
    )
}

private fun IrDeclarationContainer.classesRecursively(): Sequence<IrClass> =
    declarations.asSequence()
        .filterIsInstance<IrClass>()
        .flatMap { sequenceOf(it) + it.classesRecursively() }
