package com.yandex.daggerlite.testing.tests

import com.yandex.daggerlite.testing.tests.CompileTestDriverBase.ApiType
import javax.inject.Provider

internal fun compileTestDrivers(
    includeKsp: Boolean = true,
    includeJap: Boolean = true,
    includeRt: Boolean = true,
    includeOptimizedRt: Boolean = false,
): Collection<Provider<CompileTestDriverBase>> {
    class NamedProvider(
        private val initializer: () -> CompileTestDriverBase,
        private val name: String,
    ) : Provider<CompileTestDriverBase> {
        override fun get() = initializer()
        override fun toString() = name
    }

    val providers = buildList {
        if (includeKsp) {
            add(NamedProvider(::KspCompileTestDriver, name = "KSP"))
        }
        if (includeJap) {
            add(NamedProvider(::JapCompileTestDriver, name = "JAP"))
        }
        if (includeRt) {
            add(NamedProvider(::DynamicCompileTestDriver, name = "RT"))
        }
        if (includeOptimizedRt) {
            add(NamedProvider({ DynamicCompileTestDriver(apiType = ApiType.DynamicOptimized) }, name = "RT-optimized"))
        }
    }
    return if (CompileTestDriverBase.isInUpdateGoldenMode) {
        // No need to use all backends, use only the first included to be chosen as "golden".
        providers.take(1)
    } else providers
}