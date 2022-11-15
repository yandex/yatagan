package com.yandex.yatagan.testing.tests

import com.yandex.yatagan.processor.ksp.KspYataganProcessorProvider

class KspCompileTestDriver : CompileTestDriverBase() {
    override fun createCompilationArguments() = super.createCompilationArguments().copy(
        symbolProcessorProviders = listOf(KspYataganProcessorProvider()),
    )

    override fun generatedFilesSubDir(): String {
        return "ksp/generatedJava"
    }

    override val backendUnderTest: Backend
        get() = Backend.Ksp
}
