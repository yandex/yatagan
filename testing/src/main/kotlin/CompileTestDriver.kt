package com.yandex.daggerlite.testing

import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

interface CompileTestDriver : SourceSet {
    @get:Rule
    val testNameRule: TestNameRule

    fun givenPrecompiledModule(
        sources: SourceSet,
    )

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
