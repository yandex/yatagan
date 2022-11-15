package com.yandex.yatagan.testing.tests

import com.yandex.yatagan.processor.jap.JapYataganProcessor

class JapCompileTestDriver : CompileTestDriverBase() {
    override fun createCompilationArguments() = super.createCompilationArguments().copy(
        kaptProcessors = listOf(JapYataganProcessor()),
    )

    override fun generatedFilesSubDir(): String {
        return "kapt/kapt-java-src-out"
    }

    override val backendUnderTest: Backend
        get() = Backend.Kapt
}
