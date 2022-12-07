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

package com.yandex.yatagan.processor.common

class LoggerDecorator(
    private val wrapped: Logger,
) : Logger {
    private val lock = Any()

    override fun error(message: String) {
        synchronized(lock) {
            wrapped.error(decorateError(message))
        }
    }

    override fun warning(message: String) {
        synchronized(lock) {
            wrapped.warning(decorateWarning(message))
        }
    }

    companion object {
        fun decorateError(message: String): String {
            return ">>>[error]\n$message\n>>>"
        }

        fun decorateWarning(message: String): String {
            return ">>>[warning]\n$message\n>>>"
        }

        val MessageRegex = """>>>\[(warning|error)]\n(.*?)>>>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    }
}