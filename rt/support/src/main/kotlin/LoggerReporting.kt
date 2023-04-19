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

/**
 * An implementation of [DynamicValidationDelegate.ReportingDelegate], which is backed by a [logger].
 *
 * @param logger a logger to use for printing messages.
 */
class LoggerReporting(
    private val logger: Logger,
) : DynamicValidationDelegate.ReportingDelegate {
    override fun reportError(message: String) = report(message, "error")
    override fun reportWarning(message: String) = report(message, "warning")

    private fun report(message: String, messageKind: String) {
        logger.log("$messageKind: $message")
    }
}