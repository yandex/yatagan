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

package com.yandex.yatagan.testing.tests

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.yandex.yatagan.generated.KspProcessorClasspath
import java.io.File
import java.net.URLClassLoader

class KspCompileTestDriver(
    private val processorClasspath: String = KspProcessorClasspath,
) : CompileTestDriverBase() {
    override fun createCompilationArguments() = super.createCompilationArguments().copy(
        symbolProcessorProviders = listOf(loadProcessor()),
    )

    override fun generatedFilesSubDir(): String {
        return "ksp${File.separatorChar}generatedJava"
    }

    override val backendUnderTest: Backend
        get() = Backend.Ksp

    private fun loadProcessor(): SymbolProcessorProvider {
        val classpath = buildList {
            processorClasspath.splitToSequence(File.pathSeparatorChar).mapTo(this, ::File)
            // Include plugins classpath into processor classpath, if any present
            pluginsModuleOutputDirs?.let(::addAll)
        }

        val kspClassloader = URLClassLoader(
            "test-classloader",
            classpath.map { it.toURI().toURL() }.toTypedArray(), SymbolProcessorProvider::class.java.classLoader)
        val clazz = kspClassloader.loadClass("com.yandex.yatagan.processor.ksp.KspYataganProcessorProvider")
        return clazz.getConstructor().newInstance() as SymbolProcessorProvider
    }

    private class Child
}
