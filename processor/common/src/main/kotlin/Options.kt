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

import kotlin.reflect.KProperty

/**
 * A wrapper around a simple options map that provides safe option accessing API.
 */
class Options(
    private val values: Map<String, String>,
) {
    operator fun get(option: BooleanOption): Boolean {
        val value = values[option.key] ?: return option.default
        return when (value.lowercase()) {
            "yes", "enabled", "enable", "true" -> true
            "no", "disabled", "disable", "false" -> false
            else -> throw RuntimeException("Invalid boolean option value: $value")
        }
    }

    operator fun get(option: IntOption): Int {
        val value = values[option.key] ?: return option.default
        return value.toIntOrNull() ?: throw RuntimeException("Invalid integer option value: $value")
    }

    class BooleanOption internal constructor(
        val key: String,
        val default: Boolean,
    ) {
        operator fun getValue(delegate: ProcessorDelegate<*>, property: KProperty<*>): Boolean {
            return delegate.options[this]
        }
    }

    class IntOption internal constructor(
        val key: String,
        val default: Int,
    ) {
        operator fun getValue(delegate: ProcessorDelegate<*>, property: KProperty<*>): Int {
            return delegate.options[this]
        }
    }

    companion object {
        // TODO: Hook with docs
        val StrictMode = BooleanOption("yatagan.enableStrictMode", default = true)

        val MaxIssueEncounterPaths = IntOption("yatagan.maxIssueEncounterPaths", default = 5)

        val UsePlainOutput = BooleanOption("yatagan.usePlainOutput", default = false)
    }
}