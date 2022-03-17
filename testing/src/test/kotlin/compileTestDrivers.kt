package com.yandex.daggerlite.testing

import com.yandex.daggerlite.compiler.KspCompileTestDriver
import com.yandex.daggerlite.dynamic.DynamicCompileTestDriver
import com.yandex.daggerlite.jap.JapCompileTestDriver
import javax.inject.Provider

internal fun compileTestDrivers(): Collection<Provider<CompileTestDriverBase>> {
    class NamedProvider(
        private val initializer: () -> CompileTestDriverBase,
        private val name: String,
    ) : Provider<CompileTestDriverBase> {
        override fun get() = initializer()
        override fun toString() = name
    }

    return listOf(
        NamedProvider(::KspCompileTestDriver, name = "KSP"),
        NamedProvider(::JapCompileTestDriver, name = "JAP"),
        NamedProvider(::DynamicCompileTestDriver, name = "RT"),
    )
}
