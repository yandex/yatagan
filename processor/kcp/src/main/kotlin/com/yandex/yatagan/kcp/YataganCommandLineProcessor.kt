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

import com.yandex.yatagan.processor.common.Options
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal const val PLUGIN_ID = "com.yandex.yatagan"

internal val ProcessorOptionsKey = CompilerConfigurationKey.create<Map<String, String>>(
    "Yatagan processor options",
)

private val SharedProcessorOptions = Options.all()

@OptIn(ExperimentalCompilerApi::class)
class YataganCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = SharedProcessorOptions.map { option ->
        CliOption(
            optionName = option.key,
            valueDescription = "<value>",
            description = "Yatagan processor option ${option.key}",
            required = false,
            allowMultipleOccurrences = false,
        )
    }

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        val sharedOption = SharedProcessorOptions.singleOrNull {
            it.key == option.optionName
        } ?: throw CliOptionProcessingException("Unknown option: ${option.optionName}")
        try {
            sharedOption.parse(value)
        } catch (e: RuntimeException) {
            throw CliOptionProcessingException(e.message ?: "Invalid option value: $value", e)
        }
        configuration.put(
            ProcessorOptionsKey,
            configuration.get(ProcessorOptionsKey, emptyMap()) + (option.optionName to value),
        )
    }
}
