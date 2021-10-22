package com.yandex.daggerlite.testing

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language

internal class SourceSetImpl : SourceSet {
    override val sourceFiles: MutableList<SourceFile> = arrayListOf()

    override fun givenJavaSource(name: String, @Language("java") source: String) {
        sourceFiles += SourceFile.java(
            "${name.substringAfterLast('.')}.java",
            """package ${name.substringBeforeLast('.')};
${CommonImports.joinToString(separator = "\n") { "import $it;" }}
${source.trimIndent()} """
        )
    }

    override fun givenKotlinSource(name: String, @Language("kotlin") source: String) {
        sourceFiles += SourceFile.kotlin(
            "${name.substringAfterLast('.')}.kt",
            """package ${name.substringBeforeLast('.')}
${CommonImports.joinToString(separator = "\n") { "import $it" }}
${source.trimIndent()}"""
        )
    }
}

// bug: star imports sometimes behave incorrectly in KSP, so use explicit ones
private val CommonImports = arrayOf(
    "javax.inject.Inject",
    "javax.inject.Named",
    "javax.inject.Provider",
    "javax.inject.Scope",
    "javax.inject.Singleton",
    "dagger.Component",
    "dagger.Binds",
    "dagger.BindsInstance",
    "dagger.Provides",
    "dagger.Lazy",
    "dagger.Module",
)