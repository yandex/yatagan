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

package com.yandex.yatagan.rt.support

import com.yandex.yatagan.rt.support.SimpleDynamicValidationDelegate.InvalidGraphException
import com.yandex.yatagan.validation.RichString
import kotlin.system.measureTimeMillis

/**
 * A default implementation for a [DynamicValidationDelegate],
 * which runs validation operations eagerly and synchronously.
 *
 * @param reporting a reporting delegate to use.
 * @param logger a logger to use for additional info logging.
 * @param throwOnError `true` if implementation should raise an [InvalidGraphException]
 *   if validation found at least one error
 * @param usePlugins Whether to search classpath for Yatagan validation plugins.
 *   Bear in mind, that loading plugins can be costly for large classpaths, and may impact startup performance if
 *   used synchronously.
 */
class SimpleDynamicValidationDelegate @JvmOverloads constructor(
    private val reporting: DynamicValidationDelegate.ReportingDelegate,
    private val logger: Logger? = null,
    private val throwOnError: Boolean = true,
    override val usePlugins: Boolean = false,
) : DynamicValidationDelegate {

    /**
     * Uses [logger]-backed reporting implementation.
     */
    @JvmOverloads constructor(
        logger: Logger = ConsoleLogger(),
        throwOnError: Boolean = true,
        usePlugins: Boolean = false,
    ) : this(
        reporting = LoggerReporting(logger),
        logger = logger,
        throwOnError = throwOnError,
        usePlugins = usePlugins,
    )

    /**
     * May be raised when graph is invalid.
     */
    class InvalidGraphException(title: String) : RuntimeException() {
        override val message: String = "Validation for $title detected error(s), consult reported results for details"
    }

    override fun dispatchValidation(
        title: String,
        operation: DynamicValidationDelegate.Operation,
    ): DynamicValidationDelegate.Promise? {
        val reporting = ReportingWrapper(reporting)
        val time = measureTimeMillis {
            operation.validate(reporting)
        }
        logger?.log("Validation for $title yielded ${reporting.errorCount} error(s), " +
                "${reporting.warningCount} warning(s) and took $time ms")
        if (throwOnError && reporting.errorCount > 0) {
            throw InvalidGraphException(title)
        }
        return null
    }

    private class ReportingWrapper(
        private val delegate: DynamicValidationDelegate.ReportingDelegate,
    ) : DynamicValidationDelegate.ReportingDelegate {
        var errorCount = 0
        var warningCount = 0

        override fun reportError(message: RichString) {
            errorCount += 1
            delegate.reportError(message)
        }

        override fun reportWarning(message: RichString) {
            warningCount += 1
            delegate.reportWarning(message)
        }
    }
}
