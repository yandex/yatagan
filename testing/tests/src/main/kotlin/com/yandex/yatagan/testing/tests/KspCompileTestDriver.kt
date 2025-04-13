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
import com.yandex.yatagan.processor.ksp.KspYataganProcessorProvider
import java.io.File

class KspCompileTestDriver(
    private val useK2: Boolean,
    override val checkGoldenOutput: Boolean = true,
    apiClasspath: String = CurrentClasspath.ApiCompiled,
) : CompileTestDriverBase(
    apiClasspath = apiClasspath,
    useK2 = useK2,
) {
    override fun createCompilationArguments() = super.createCompilationArguments().copy(
        symbolProcessorProviders = listOf(KspYataganProcessorProvider()),
    )

    override fun generatedFilesSubDir(): String {
        return "ksp${File.separatorChar}generatedJava"
    }

    override val backendUnderTest: Backend
        get() = if (useK2) Backend.Ksp2 else Backend.Ksp
}
