package com.yandex.daggerlite.testing.source_set

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import java.io.File

internal class SourceSetImpl : SourceSet {
    private val _sourceFiles: MutableList<SourceFile> = arrayListOf()

    override val sourceFiles: List<SourceFile> get() = _sourceFiles.toList()

    override fun givenJavaSource(name: String, @Language("java") source: String) {
        _sourceFiles += if ('.' in name) {
            SourceFile.java(
                name = "${name.substringAfterLast('.')}.java",
                contents = "package ${name.substringBeforeLast('.')};\n" + source,
            )
        } else {
            SourceFile.java(
                name = "$name.java",
                contents = source,
            )
        }
    }

    override fun givenKotlinSource(name: String, @Language("kotlin") source: String) {
        _sourceFiles += if ('.' in name) {
            SourceFile.kotlin(
                name = "${name.substringAfterLast('.')}.kt",
                contents = "package ${name.substringBeforeLast('.')}\n" + source,
            )
        } else {
             SourceFile.kotlin(
                name = "$name.kt",
                contents = source,
            )
        }
    }

    override fun includeFromSourceSet(sourceSet: SourceSet) {
        _sourceFiles += sourceSet.sourceFiles
    }

    override fun includeAllFromDirectory(sourceDir: File) {
        require(sourceDir.isDirectory) {
            "`$sourceDir` is not a directory"
        }

        sourceDir.walk()
            .filter { it.isFile && it.extension == "kt" || it.extension == "java" }
            .map(SourceFile::fromPath)
            .forEach(_sourceFiles::add)
    }
}
