package com.yandex.daggerlite.testing.support

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language

interface SourceSet {
    val sourceFiles: List<SourceFile>

    fun givenJavaSource(name: String, @Language("java") source: String)
    fun givenKotlinSource(name: String, @Language("kotlin") source: String)
    fun includeFromSourceSet(sourceSet: SourceSet)
}

inline fun SourceSet(block: SourceSet.() -> Unit): SourceSet = SourceSetImpl().apply(block)