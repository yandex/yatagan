package com.yandex.daggerlite.testing

import com.yandex.daggerlite.testing.CompileTestDriverBase.ApiType
import javax.inject.Provider

internal fun compileTestDrivers(
    includeOptimizedRt: Boolean = false,
): Collection<Provider<CompileTestDriverBase>> {
    class NamedProvider(
        private val initializer: () -> CompileTestDriverBase,
        private val name: String,
    ) : Provider<CompileTestDriverBase> {
        override fun get() = initializer()
        override fun toString() = name
    }

    return buildList {
        add(NamedProvider(::KspCompileTestDriver, name = "KSP"))
        add(NamedProvider(::JapCompileTestDriver, name = "JAP"))
        add(NamedProvider(::DynamicCompileTestDriver, name = "RT"))
        if (includeOptimizedRt) {
            add(NamedProvider({ DynamicCompileTestDriver(apiType = ApiType.DynamicOptimized) }, name = "RT-optimized"))
        }
    }
}
