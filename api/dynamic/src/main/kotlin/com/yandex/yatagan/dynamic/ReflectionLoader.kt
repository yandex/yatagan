/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.dynamic

import com.yandex.yatagan.AutoBuilder
import com.yandex.yatagan.internal.ImplementationLoader
import com.yandex.yatagan.internal.YataganInternal
import com.yandex.yatagan.rt.engine.RuntimeEngine
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import java.io.InputStream
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

private const val REFLECTION_PROPERTIES_RESOURCE_PATH =
    "META-INF/com.yandex.yatagan.reflection/parameters.properties"

private const val VALIDATION_DELEGATE_CLASS_PROPERTY = "validationDelegateClass"
private const val MAX_ISSUE_ENCOUNTER_PATHS_PROPERTY = "maxIssueEncounterPaths"
private const val IS_STRICT_MODE_PROPERTY = "enableStrictMode"
private const val USE_PLAIN_OUTPUT_PROPERTY = "usePlainOutput"
private const val DAGGER_COMPATIBILITY = "enableDaggerCompatibility"
private const val THREAD_CHECKER_CLASS_NAME = "threadCheckerClassName"

/**
 * Instantiated reflectively.
 * WARNING: Keep class name in sync with [com.yandex.yatagan.Yatagan.DYNAMIC_LOADER_DELEGATE_CLASS_NAME].
 */
@PublishedApi
@OptIn(YataganInternal::class)
internal class ReflectionLoader : ImplementationLoader by ReflectionLoader {
    internal companion object : ImplementationLoader {
        private val engine: RuntimeEngine

        init {
            val threadFactory = ThreadFactory {
                Thread(it, "yatagan-params-loader").apply {
                    if (!isDaemon) isDaemon = true
                    if (priority != Thread.NORM_PRIORITY) priority = Thread.NORM_PRIORITY
                }
            }
            val executor = Executors.newSingleThreadExecutor(threadFactory)
            val params = executor.submit<RuntimeEngine.Params> {
                val paramsStream: InputStream? =
                    ReflectionLoader::class.java.classLoader.getResourceAsStream(REFLECTION_PROPERTIES_RESOURCE_PATH)

                val properties = paramsStream?.let { input ->
                    Properties().apply {
                        input.use { load(it) }
                    }
                }

                val params = RuntimeEngine.Params()
                properties?.entries?.forEach { (property, value) ->
                    when(property) {
                        VALIDATION_DELEGATE_CLASS_PROPERTY -> {
                            try {
                                params.validationDelegate = Class.forName(value.toString())
                                    .getConstructor()
                                    .newInstance() as DynamicValidationDelegate
                            } catch (e: ClassNotFoundException) {
                                throw IllegalStateException("Invalid `$property` value", e)
                            }
                        }
                        MAX_ISSUE_ENCOUNTER_PATHS_PROPERTY -> {
                            params.maxIssueEncounterPaths = value.toString().toIntOrNull()
                                ?: throw IllegalStateException("Expected integer for `$property`, got `$value`")
                        }
                        IS_STRICT_MODE_PROPERTY -> {
                            params.isStrictMode = value.toString().toBooleanStrictOrNull()
                                ?: throw IllegalStateException("Expected boolean for `$property`, got `$value`")
                        }
                        USE_PLAIN_OUTPUT_PROPERTY -> {
                            params.usePlainOutput = value.toString().toBooleanStrictOrNull()
                                ?: throw IllegalStateException("Expected boolean for `$property`, got `$value`")
                        }
                        DAGGER_COMPATIBILITY -> {
                            params.enableDaggerCompatibility = value.toString().toBooleanStrictOrNull()
                                ?: throw IllegalStateException("Expected boolean for `$property`, got `$value`")
                        }
                        THREAD_CHECKER_CLASS_NAME -> {
                            params.threadCheckerClassName = value?.toString()
                        }
                        else -> {
                            throw IllegalStateException("Unknown property `$property`")
                        }
                    }
                }
                params
            }
            executor.shutdown()
            engine = RuntimeEngine(params)
        }

        override fun <T : Any> builder(builderClass: Class<T>): Result<T> {
            return Result.success(engine.builder(builderClass))
        }

        override fun <T : Any> autoBuilder(componentClass: Class<T>): Result<AutoBuilder<T>> {
            return Result.success(engine.autoBuilder(componentClass))
        }
    }
}
