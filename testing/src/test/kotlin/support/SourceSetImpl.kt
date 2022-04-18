package com.yandex.daggerlite.testing.support

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import java.io.File

class SourceSetImpl : SourceSet {
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

    override fun includeFromSourceSet(sourceSet: SourceSet) {
        sourceFiles += sourceSet.sourceFiles
    }

    override fun includeAllFromDirectory(sourceDir: File) {
        sourceDir.walk()
            .filter { it.isFile && it.extension == "kt" || it.extension == "java" }
            .map(SourceFile::fromPath)
            .forEach(sourceFiles::add)
    }
}

