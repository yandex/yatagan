package com.yandex.daggerlite.testing.source_set

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import java.io.File

interface SourceSet {
    /**
     * All sources, currently included into the source set.
     */
    val sourceFiles: List<SourceFile>

    /**
     * Adds a Java source to the source set.
     *
     * @param name a dot-separated qualified name of a source file. package-name is derived from this.
     * @param source source code in Java. No package directive is expected.
     */
    fun givenJavaSource(name: String, @Language("java") source: String)

    /**
     * Adds a Kotlin source to the source set.
     *
     * @param name a dot-separated qualified name of a source file. package-name is derived from this.
     * @param source source code in Kotlin. No package directive is expected.
     */
    fun givenKotlinSource(name: String, @Language("kotlin") source: String)

    /**
     * Includes all currently added sources from the [sourceSet]. No relations are established between source sets.
     * If any changes to be done later to the given [sourceSet], they will not be reflected here.
     */
    fun includeFromSourceSet(sourceSet: SourceSet)

    /**
     * Includes all currently present sources with "java" or "kt" extensions from the given [sourceDir].
     *
     * @throws IllegalArgumentException if [sourceDir] is not a directory.
     */
    fun includeAllFromDirectory(sourceDir: File)
}

/**
 * Constructor function for [SourceSet]s.
 */
fun SourceSet(block: SourceSet.() -> Unit = {}): SourceSet = SourceSetImpl().apply(block)