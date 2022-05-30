package com.yandex.daggerlite.testing

import com.tschuchort.compiletesting.KotlinCompilation
import com.yandex.daggerlite.jap.JapDaggerLiteProcessor
import java.io.File

class JapCompileTestDriver : CompileTestDriverBase() {
    override fun createKotlinCompilation() = super.createKotlinCompilation().apply {
        sources = sourceFiles
        annotationProcessors = listOf(JapDaggerLiteProcessor())
    }

    override fun getGeneratedFiles(from: KotlinCompilation): Collection<File> {
        return from.kaptSourceDir.walk()
            .filter { it.isFile && it.extension == "java" }
            .toList()
    }

    override val backendUnderTest: Backend
        get() = Backend.Kapt
}
