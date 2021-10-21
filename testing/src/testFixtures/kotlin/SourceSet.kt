package com.yandex.daggerlite.testing

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language

interface SourceSet {
    val sourceFiles: Collection<SourceFile>

    fun givenJavaSource(name: String, @Language("java") source: String)
    fun givenKotlinSource(name: String, @Language("kotlin") source: String)
}