package com.yandex.daggerlite.testing

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.process.LoggerDecorator
import kotlin.test.assertContains
import kotlin.test.assertEquals

abstract class CompileTestDriverBase : CompileTestDriver {
    private val sourceSet = SourceSetImpl()

    final override val sourceFiles: List<SourceFile>
        get() = sourceSet.sourceFiles

    final override fun givenJavaSource(name: String, source: String) {
        sourceSet.givenJavaSource(name, source)
    }

    final override fun givenKotlinSource(name: String, source: String) {
        sourceSet.givenKotlinSource(name, source)
    }

    final override fun givenSourceSet(block: SourceSet.() -> Unit): SourceSet {
        return SourceSetImpl().apply(block)
    }

    final override fun useSourceSet(sources: SourceSet) {
        sourceSet.sourceFiles += sources.sourceFiles
    }

    protected fun KotlinCompilation.basicKotlinCompilationSetup() {
        verbose = false
        inheritClassPath = true
        javacArguments += "-Xdiags:verbose"
    }

    protected abstract class CompilationResultClauseBase(
        private val result: KotlinCompilation.Result,
        private val compiledClassesLoader: ClassLoader?,
    ) : CompileTestDriver.CompilationResultClause {
        override fun withError(message: String, block: ((notes: String) -> Unit)?) {
            val errors = result.parsedMessages().filter { it.kind == Message.Kind.Error }.toList()
            assertContains(errors.map(Message::text), message)
            if (block != null) {
                errors.find { it.text == message }?.let { block(it.notes) }
            }
        }

        override fun withWarning(message: String, block: ((notes: String) -> Unit)?) {
            val warnings = result.parsedMessages().filter { it.kind == Message.Kind.Warning }.toList()
            assertContains(warnings.map(Message::text), message)
            if (block != null) {
                warnings.find { it.text == message }?.let { block(it.notes) }
            }
        }

        override fun withNoWarnings() {
            assertEquals(emptyList(), result.parsedMessages().filter { it.kind == Message.Kind.Warning }.toList())
        }

        override fun withNoErrors() {
            assertEquals(emptyList(), result.parsedMessages().filter { it.kind == Message.Kind.Error }.toList())
        }

        override fun inspectGeneratedClass(name: String, callback: (Class<*>) -> Unit) {
            if (result.exitCode == KotlinCompilation.ExitCode.OK) {
                compiledClassesLoader?.let { callback(it.loadClass(name)) }
            }
        }
    }

    protected data class Message(
        val kind: Kind,
        val text: String,
        val notes: String,
    ) {
        enum class Kind {
            Error,
            Warning,
        }

        override fun toString() = "$kind: $text"
    }

    companion object {
        private val AnsiColorSeqRegex = "\u001b.*?m".toRegex()

        private fun KotlinCompilation.Result.parsedMessages(): Sequence<Message> {
            return LoggerDecorator.MessageRegex.findAll(messages).map { messageMatch ->
                val (kind, message, notes) = messageMatch.destructured
                Message(
                    kind = when (kind) {
                        "error" -> Message.Kind.Error
                        "warning" -> Message.Kind.Warning
                        else -> throw AssertionError()
                    },
                    text = message.replace(AnsiColorSeqRegex, "").trim(),
                    notes = notes.replace(AnsiColorSeqRegex, "").trimIndent(),
                )
            }.memoize()
        }
    }
}
