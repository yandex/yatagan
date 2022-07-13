package com.yandex.daggerlite.process

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

    class BooleanOption internal constructor(
        val key: String,
        val default: Boolean,
    ) {
        operator fun getValue(delegate: ProcessorDelegate<*>, property: KProperty<*>): Boolean {
            return delegate.options[this]
        }
    }

    companion object {
        // TODO: Hook with docs
        val UseParallelProcessing = BooleanOption("daggerlite.experimental.useParallelProcessing", default = false)

        val StrictMode = BooleanOption("daggerlite.enableStrictMode", default = true)
    }
}