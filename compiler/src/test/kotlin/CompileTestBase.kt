package com.yandex.dagger3.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.intellij.lang.annotations.Language
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.expect

interface SourceSet {
    val sourceFiles: Collection<SourceFile>
    fun givenJavaSource(name: String, @Language("java") source: String)
    fun givenKotlinSource(name: String, @Language("kotlin") source: String)
    fun useSourceSet(sourceSet: SourceSet)
}

abstract class CompileTestBase : SourceSet by SourceSetImpl() {
    private class SourceSetImpl : SourceSet {
        override val sourceFiles = arrayListOf<SourceFile>()

        override fun givenJavaSource(name: String, @Language("java") source: String) {
            sourceFiles += SourceFile.java(
                "${name.substringAfterLast('.')}.java", """
            package ${name.substringBeforeLast('.')};
            ${CommonImports.joinToString(separator = "\n") { "import $it;" }}
            $source
        """.trimIndent()
            )
        }

        override fun givenKotlinSource(name: String, @Language("kotlin") source: String) {
            sourceFiles += SourceFile.kotlin(
                "${name.substringAfterLast('.')}.kt", """
            package ${name.substringBeforeLast('.')}
            ${CommonImports.joinToString(separator = "\n") { "import $it" }}
            $source
        """.trimIndent()
            )
        }

        override fun useSourceSet(sourceSet: SourceSet) {
            sourceFiles += sourceSet.sourceFiles
        }
    }

    protected fun givenSourceSet(block: SourceSet.() -> Unit): SourceSet = SourceSetImpl().apply(block)

    protected fun failsToCompile(block: CompilationResulClause.() -> Unit) {
        expect(KotlinCompilation.ExitCode.COMPILATION_ERROR) {
            val compilation = setupFirstRoundCompilation()
            val result = compilation.compile()
            CompilationResulClause(compilation, result).block()
            result.exitCode
        }
    }

    protected fun assertCompilesSuccessfully(block: CompilationResulClause.() -> Unit = {}) {
        val firstRound = setupFirstRoundCompilation()
        try {
            expect(KotlinCompilation.ExitCode.OK) {
                val result = firstRound.compile()
                CompilationResulClause(firstRound, result).apply {
                    withNoErrors()
                    block()
                }
                result.exitCode
            }
            expect(KotlinCompilation.ExitCode.OK) {
                // Second round is required to validate that generated code compiles successfully
                val secondRound = KotlinCompilation().apply {
                    sources = firstRound.sources + firstRound.kspGeneratedSources().map(SourceFile::fromPath)
                    inheritClassPath = true
                }
                val result = secondRound.compile()
                CompilationResulClause(secondRound, result).apply {
                    withNoErrors()
                }
                result.exitCode
            }
        } catch (e: Throwable) {
            firstRound.kspGeneratedSources().forEach { file ->
                System.err.println("Content of the $file")
                System.err.println(file.readText())
            }
            throw e
        }
    }

    private fun setupFirstRoundCompilation() = KotlinCompilation().apply {
        sources = sourceFiles.toList()
        inheritClassPath = true
        symbolProcessorProviders = listOf(Dagger3ProcessorProvider())
    }

    protected inner class CompilationResulClause(
        private val compilation: KotlinCompilation,
        private val result: KotlinCompilation.Result,
    ) {
        fun withErrorContaining(message: String) {
            assertContains(result.kspMessages(), Message(Message.Kind.ERROR, message))
        }

        fun withWarningContaining(message: String) {
            assertContains(result.kspMessages(), Message(Message.Kind.WARNING, message))
        }

        fun withNoWarnings() {
            assertEquals(emptyList(), result.kspMessages().filter { it.kind == Message.Kind.WARNING })
        }

        fun withNoErrors() {
            assertEquals(emptyList(), result.kspMessages().filter { it.kind == Message.Kind.ERROR })
        }

        fun generatesJavaSources(name: String) {
            assertContains(
                compilation.kspSourcesDir
                    .resolve("java")
                    .resolve(name.substringBeforeLast('.').replace('.', '/'))
                    .listFiles()
                    ?.map { it.name } ?: emptyList(),
                "${name.substringAfterLast('.')}.java",
            )
        }
    }
}

internal fun KotlinCompilation.kspGeneratedSources(): Sequence<File> {
    return kspSourcesDir.walk()
        .filter { it.isFile && it.extension in SupportedSourceExtensions }
}


internal fun KotlinCompilation.Result.kspMessages(): List<Message> {
    return messages.lineSequence().mapNotNull { line ->
        ProcessorMessageRegex.matchEntire(line)?.let { result ->
            val (kind, text) = result.destructured
            Message(
                kind = when (kind) {
                    "w" -> Message.Kind.WARNING
                    "e" -> Message.Kind.ERROR
                    else -> throw IllegalStateException()
                },
                text = text,
            )
        }
    }.toList()
}

internal data class Message(
    val kind: Kind,
    val text: String,
) {
    enum class Kind {
        ERROR,
        WARNING,
    }

    override fun toString(): String {
        return "$kind: $text"
    }
}

private val ProcessorMessageRegex = """^([we]): \[ksp] (.*)$""".toRegex()
private val SupportedSourceExtensions = arrayOf("java", "kt")

// bug: star imports sometimes behave incorrectly in KSP, so use explicit ones
private val CommonImports = arrayOf(
    "javax.inject.Inject",
    "javax.inject.Named",
    "javax.inject.Provider",
    "javax.inject.Singleton",
    "dagger.Component",
    "dagger.Binds",
    "dagger.BindsInstance",
    "dagger.Provides",
    "dagger.Lazy",
    "dagger.Module",
)