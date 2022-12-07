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

import com.yandex.yatagan.validation.RichString

/**
 * An implementation of [DynamicValidationDelegate.ReportingDelegate], which is backed by a [logger].
 *
 * @param logger a logger to use for printing messages.
 * @param useAnsiColor if `true`, then messages will be colored using ANSI control sequences.
 *   Makes sense to use if the [logger] prints to a terminal or any other tool with ANSI-sequence support.
 *   `false` means the output is plain string.
 */
class LoggerReporting(
    private val logger: Logger,
    private val useAnsiColor: Boolean = false,
): DynamicValidationDelegate.ReportingDelegate {
    override fun reportError(message: RichString) = report(message, "error")
    override fun reportWarning(message: RichString) = report(message, "warning")

    private fun report(message: RichString, messageKind: String) {
        val text = if (useAnsiColor) message.toAnsiEscapedString() else message.toString()
        logger.log("$messageKind: $text")
    }
}