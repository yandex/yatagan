package com.yandex.daggerlite.testing

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language

internal class SourceSetImpl : SourceSet {
    override val sourceFiles: MutableList<SourceFile> = arrayListOf()

    override fun givenJavaSource(name: String, @Language("java") source: String) {
        sourceFiles += SourceFile.java(
            "${name.substringAfterLast('.')}.java",
            """package ${name.substringBeforeLast('.')};
$source"""
        )
    }

    override fun givenKotlinSource(name: String, @Language("kotlin") source: String) {
        sourceFiles += SourceFile.kotlin(
            "${name.substringAfterLast('.')}.kt",
            """package ${name.substringBeforeLast('.')}
$source"""
        )
    }
}