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

import com.yandex.yatagan.generated.KaptProcessorClasspath
import java.io.File
import java.net.URLClassLoader
import javax.annotation.processing.Processor

class JapCompileTestDriver(
    private val processorClasspath: String = KaptProcessorClasspath,
    override val checkGoldenOutput: Boolean = true,
) : CompileTestDriverBase() {
    override fun createCompilationArguments() = super.createCompilationArguments().copy(
        kaptProcessors = listOf(loadProcessor()),
    )

    override fun generatedFilesSubDir(): String {
        return "kapt${File.separatorChar}kapt-java-src-out"
    }

    override val backendUnderTest: Backend
        get() = Backend.Kapt

    private fun loadProcessor(): Processor {
        val classpath = buildList {
            processorClasspath.split(File.pathSeparatorChar).mapTo(this, ::File)
            // Include plugins classpath into processor classpath, if any present
            pluginsModuleOutputDirs?.let(::addAll)
        }

        val kaptClassloader = URLClassLoader(
            classpath.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader())
        val clazz = kaptClassloader.loadClass("com.yandex.yatagan.processor.jap.JapYataganProcessor")
        return clazz.getConstructor().newInstance() as Processor
    }
}
