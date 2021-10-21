package com.yandex.daggerlite.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.yandex.daggerlite.testing.CompileTestDriver
import com.yandex.daggerlite.testing.CompileTestDriverBase
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.expect

class KspCompileTestDriver : CompileTestDriverBase() {
    override fun failsToCompile(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        expect(KotlinCompilation.ExitCode.COMPILATION_ERROR) {
            val compilation = setupFirstRoundCompilation()
            val result = compilation.compile()
            KspCompilationResultClause(compilation, result).block()
            result.exitCode
        }
    }

    override fun compilesSuccessfully(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        val firstRound = setupFirstRoundCompilation()
        try {
            expect(KotlinCompilation.ExitCode.OK) {
                val result = firstRound.compile()
                KspCompilationResultClause(firstRound, result).apply {
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
                KspCompilationResultClause(secondRound, result).apply {
                    withNoErrors()
                }
                result.exitCode
            }
        } finally {
            firstRound.kspGeneratedSources().forEach { file ->
                println("Content of the file://$file")
                println(file.readText())
            }
        }
    }

    private fun setupFirstRoundCompilation() = KotlinCompilation().apply {
        sources = sourceFiles.toList()
        inheritClassPath = true
        symbolProcessorProviders = listOf(DaggerLiteProcessorProvider())
    }

    private inner class KspCompilationResultClause(
        private val compilation: KotlinCompilation,
        private val result: KotlinCompilation.Result,
    ) : CompileTestDriver.CompilationResultClause {
        override fun withErrorContaining(message: String) {
            assertContains(result.kspMessages(), Message(Message.Kind.ERROR, message))
        }

        override fun withWarningContaining(message: String) {
            assertContains(result.kspMessages(), Message(Message.Kind.WARNING, message))
        }

        override fun withNoWarnings() {
            assertEquals(emptyList(), result.kspMessages().filter { it.kind == Message.Kind.WARNING })
        }

        override fun withNoErrors() {
            assertEquals(emptyList(), result.kspMessages().filter { it.kind == Message.Kind.ERROR })
        }

        override fun generatesJavaSources(name: String) {
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

private fun KotlinCompilation.kspGeneratedSources(): Sequence<File> {
    return kspSourcesDir.walk()
        .filter { it.isFile && it.extension in SupportedSourceExtensions }
}


private fun KotlinCompilation.Result.kspMessages(): List<Message> {
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
