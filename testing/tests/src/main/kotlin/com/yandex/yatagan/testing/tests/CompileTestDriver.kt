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

import com.yandex.yatagan.processor.common.Option
import com.yandex.yatagan.testing.source_set.SourceSet
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

interface CompileTestDriver : SourceSet {
    @get:Rule
    val testNameRule: TestNameRule

    fun givenPrecompiledModule(
        sources: SourceSet,
    )

    fun <V : Any> givenOption(option: Option<V>, value: V)

    /**
     * Runs the test and validates the output against the golden output file.
     * The golden output file resource path is computed from the junit test name.
     */
    fun compileRunAndValidate()

    val backendUnderTest: Backend
}

class TestNameRule : TestWatcher() {
    var testClassSimpleName: String? = ""
    var testMethodName: String = "no-test"

    fun assignFrom(other: TestNameRule) {
        testClassSimpleName = other.testClassSimpleName
        testMethodName = other.testMethodName
    }

    override fun starting(description: Description) {
        testClassSimpleName = description.className.substringAfterLast('.')
        testMethodName = description.methodName
            .replace("""\[.*?]$""".toRegex(), "")  // Remove backend+api test suffix, e.g. [JAP], [KSP], ... .
            .replace("""\W+""".toRegex(), "-")  // Replace every sequence of non-word characters by a '-'.
    }
}

enum class Backend {
    Kapt,
    Ksp,
    Rt,
}
