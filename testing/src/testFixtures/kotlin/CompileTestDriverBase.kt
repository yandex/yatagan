package com.yandex.daggerlite.testing

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile

abstract class CompileTestDriverBase : CompileTestDriver {
    private val sourceSet = SourceSetImpl()

    final override val sourceFiles: List<SourceFile>
        get() = sourceSet.sourceFiles

    final override fun givenJavaSource(name: String, source: String) {
        sourceSet.givenJavaSource(name, source)
    }

    final override fun givenKotlinSource(name: String, source: String) {
        sourceSet.givenKotlinSource(name, source)
    }

    final override fun givenSourceSet(block: SourceSet.() -> Unit): SourceSet {
        return SourceSetImpl().apply(block)
    }

    final override fun useSourceSet(sources: SourceSet) {
        sourceSet.sourceFiles += sources.sourceFiles
    }

    protected fun KotlinCompilation.basicKotlinCompilationSetup() {
        verbose = false
        inheritClassPath = true
        javacArguments += "-Xdiags:verbose"
    }
}
