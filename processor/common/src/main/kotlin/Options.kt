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

/**
 * A wrapper around a simple options map that provides safe option accessing API.
 */
class Options(
    private val values: Map<String, String>,
) {
    operator fun <T> get(option: Option<T>): T {
        val value = values[option.key] ?: return option.default
        return option.parse(value)
    }

    companion object {
        fun all(): Set<Option<*>> = buildSet {
            @Suppress("NO_REFLECTION_IN_CLASS_PATH")
            for (sealedSubclass in Option::class.sealedSubclasses) {
                check(sealedSubclass.java.isEnum) { "Unexpected option class" }
                addAll(sealedSubclass.java.enumConstants)
            }
        }
    }
}

sealed interface Option<out T> {
    val key: String
    val default: T
    fun parse(rawValue: String): T
}

enum class BooleanOption(
    override val key: String,
    override val default: Boolean,
) : Option<Boolean> {

    StrictMode("yatagan.enableStrictMode", default = true),
    UsePlainOutput("yatagan.usePlainOutput", default = false),
    AllConditionsLazy("yatagan.experimental.allConditionsLazy", default = false),
    OmitThreadChecks("yatagan.experimental.omitThreadChecks", default = false),
    OmitProvisionNullChecks("yatagan.experimental.omitProvisionNullChecks", default = false),
    SortMethodsForTesting("yatagan.internal.testing.sortMethods", default = false),

    ;

    override fun parse(rawValue: String): Boolean {
        return when (rawValue.lowercase()) {
            "yes", "enabled", "enable", "true" -> true
            "no", "disabled", "disable", "false" -> false
            else -> throw RuntimeException("Invalid boolean option value: $rawValue")
        }
    }
}

enum class IntOption(
    override val key: String,
    override val default: Int,
) : Option<Int> {

    MaxIssueEncounterPaths("yatagan.maxIssueEncounterPaths", default = 5),
    MaxSlotsPerSwitch("yatagan.experimental.maxSlotsPerSwitch", default = -1),

    ;

    override fun parse(rawValue: String): Int {
        return rawValue.toIntOrNull() ?: throw RuntimeException("Invalid integer option value: $rawValue")
    }
}

