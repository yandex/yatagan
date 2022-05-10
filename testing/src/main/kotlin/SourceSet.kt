package com.yandex.daggerlite.testing

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import java.io.File

interface SourceSet {
    val sourceFiles: List<SourceFile>

    fun givenJavaSource(name: String, @Language("java") source: String)
    fun givenKotlinSource(name: String, @Language("kotlin") source: String)
    fun includeFromSourceSet(sourceSet: SourceSet)
    fun includeAllFromDirectory(sourceDir: File)
}

inline fun SourceSet(block: SourceSet.() -> Unit): SourceSet = SourceSetImpl().apply(block)