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

import com.yandex.yatagan.generated.CurrentClasspath
import com.yandex.yatagan.processor.jap.JapYataganProcessor
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import javax.annotation.processing.Processor

class JapCompileTestDriver(
    private val customProcessorClasspath: String? = null,
    override val checkGoldenOutput: Boolean = true,
    apiClasspath: String = CurrentClasspath.ApiCompiled,
): CompileTestDriverBase(
    apiClasspath = apiClasspath,
) {
    override fun createCompilationArguments() = super.createCompilationArguments().copy(
        kaptProcessors = listOf(loadProcessorFromCustomClasspath() ?: JapYataganProcessor()),
    )

    override fun generatedFilesSubDir(): String {
        return "kapt${File.separatorChar}kapt-java-src-out"
    }

    override val backendUnderTest: Backend
        get() = Backend.Kapt

    private fun loadProcessorFromCustomClasspath(): Processor? {
        customProcessorClasspath ?: return null
        val kaptClassloader = URLClassLoader(customProcessorClasspath.split(File.pathSeparatorChar)
            .map { File(it).toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader())
        return ServiceLoader.load(Processor::class.java, kaptClassloader).toList().single()
    }
}
