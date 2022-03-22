package com.yandex.daggerlite.testing.support

import com.tschuchort.compiletesting.KotlinCompilation
import com.yandex.daggerlite.jap.JapDaggerLiteProcessor

class JapCompileTestDriver : CompileTestDriverBase() {
    override fun createKotlinCompilation(): KotlinCompilation {
        return super.createKotlinCompilation().apply {
            sources = sourceFiles
            annotationProcessors = listOf(JapDaggerLiteProcessor())
        }
    }

    override val backendUnderTest: Backend
        get() = Backend.Kapt
}
