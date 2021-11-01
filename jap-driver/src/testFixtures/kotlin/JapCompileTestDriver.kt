package com.yandex.daggerlite.jap

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.kspSourcesDir
import com.yandex.daggerlite.testing.CompileTestDriver
import com.yandex.daggerlite.testing.CompileTestDriverBase
import java.io.File
import java.net.URLClassLoader
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.expect

class JapCompileTestDriver : CompileTestDriverBase() {
    override fun failsToCompile(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        val compilation = setupCompilation()
        expect(KotlinCompilation.ExitCode.COMPILATION_ERROR) {
            val result = compilation.compile()
            JapCompilationResultClause(
                generation = compilation,
                result = result,
                compiledClassesLoader = null,
            ).block()

            compilation.japGeneratedSources().forEach { file ->
                println("Content of the file://$file")
            }

            result.exitCode
        }
    }

    override fun compilesSuccessfully(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        val compilation = setupCompilation()
        expect(KotlinCompilation.ExitCode.OK) {
            val result = compilation.compile()
            JapCompilationResultClause(
                generation = compilation,
                result = result,
                compiledClassesLoader = URLClassLoader(
                    arrayOf(compilation.classesDir.toURI().toURL()),
                    JapCompileTestDriver::class.java.classLoader
                ),
            ).apply {
                withNoErrors()
                block()
            }

            compilation.japGeneratedSources().forEach { file ->
                println("Content of the file://$file")
            }

            result.exitCode
        }
    }

    fun setupCompilation() = KotlinCompilation().apply {
        sources = sourceFiles
        inheritClassPath = true
        annotationProcessors = listOf(JapDaggerLiteProcessor())
    }
}


private class JapCompilationResultClause(
    private val generation: KotlinCompilation?,
    private val result: KotlinCompilation.Result,
    private val compiledClassesLoader: ClassLoader?,
) : CompileTestDriver.CompilationResultClause {

    override fun withErrorContaining(message: String) {
        assertContains(result.japMessages(), Message(Message.Kind.ERROR, message))
    }

    override fun withWarningContaining(message: String) {
        assertContains(result.japMessages(), Message(Message.Kind.WARNING, message))
    }

    override fun withNoWarnings() {
        assertEquals(emptyList(), result.japMessages().filter { it.kind == Message.Kind.WARNING })
    }

    override fun withNoErrors() {
        assertEquals(emptyList(), result.japMessages().filter { it.kind == Message.Kind.ERROR })
    }

    override fun generatesJavaSources(name: String) {
        if (generation != null) {
            assertContains(
                generation.kaptSourceDir
                    .resolve(name.substringBeforeLast('.').replace('.', '/'))
                    .listFiles()
                    ?.map { it.name } ?: emptyList(),
                "${name.substringAfterLast('.')}.java",
            )
        }
    }

    override fun inspectGeneratedClass(name: String, callback: (Class<*>) -> Unit) {
        if (result.exitCode == KotlinCompilation.ExitCode.OK) {
            compiledClassesLoader?.let { callback(it.loadClass(name)) }
        }
    }

    private fun KotlinCompilation.Result.japMessages(): List<Message> {
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

    private data class Message(
        val kind: Kind,
        val text: String
    ) {
        enum class Kind { ERROR, WARNING }

        override fun toString() = "$kind: $text"
    }
}

private fun KotlinCompilation.japGeneratedSources(): Sequence<File> {
    return kaptSourceDir.walk().filter { it.isFile && it.extension == "java" }
}

private val ProcessorMessageRegex = """^([we]): \[jap] (.*)$""".toRegex()