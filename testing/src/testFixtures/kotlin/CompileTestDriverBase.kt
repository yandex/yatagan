package com.yandex.daggerlite.testing

import com.tschuchort.compiletesting.SourceFile

abstract class CompileTestDriverBase : CompileTestDriver {
    private val sourceSet = SourceSetImpl()

    final override val sourceFiles: Collection<SourceFile>
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
}
