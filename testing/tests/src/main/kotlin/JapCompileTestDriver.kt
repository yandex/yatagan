package com.yandex.daggerlite.testing.tests

import com.tschuchort.compiletesting.KotlinCompilation
import com.yandex.daggerlite.processor.common.Options
import com.yandex.daggerlite.processor.jap.JapDaggerLiteProcessor
import java.io.File

class JapCompileTestDriver : CompileTestDriverBase() {
    override fun createKotlinCompilation() = super.createKotlinCompilation().apply {
        sources = sourceFiles
        annotationProcessors = listOf(JapDaggerLiteProcessor())
        kaptArgs[Options.MaxIssueEncounterPaths.key] = "100"
    }

    override fun getGeneratedFiles(from: KotlinCompilation): Collection<File> {
        return from.kaptSourceDir.walk()
            .filter { it.isFile && it.extension == "java" }
            .toList()
    }

    override val backendUnderTest: Backend
        get() = Backend.Kapt

}
