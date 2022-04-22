package com.yandex.daggerlite.testing.support

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.generated.CompiledApiClasspath
import com.yandex.daggerlite.generated.DynamicApiClasspath
import com.yandex.daggerlite.process.LoggerDecorator
import org.junit.Assert
import java.io.File
import java.net.URLClassLoader

abstract class CompileTestDriverBase protected constructor(
    protected val apiType: ApiType = ApiType.Compiled,
) : CompileTestDriver {
    private val mainSourceSet = SourceSetImpl()
    private var precompiledModuleOutputDir: File? = null

    final override val sourceFiles: List<SourceFile>
        get() = mainSourceSet.sourceFiles

    final override fun givenJavaSource(name: String, source: String) {
        mainSourceSet.givenJavaSource(name, source)
    }

    final override fun givenKotlinSource(name: String, source: String) {
        mainSourceSet.givenKotlinSource(name, source)
    }

    final override fun includeFromSourceSet(sourceSet: SourceSet) {
        mainSourceSet.includeFromSourceSet(sourceSet)
    }

    override fun includeAllFromDirectory(sourceDir: File) {
        mainSourceSet.includeAllFromDirectory(sourceDir)
    }

    final override fun givenPrecompiledModule(sources: SourceSet) {
        check(precompiledModuleOutputDir == null) {
            "Can't have multiple precompiled modules"
        }

        val compilation = createBaseKotlinCompilation().apply {
            this.sources = sources.sourceFiles
        }
        val result = compilation.compile()
        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            throw RuntimeException("Pre-compilation failed, check the code")
        }

        precompiledModuleOutputDir = result.outputDirectory
    }

    data class ValidationResult(
        val runtimeClasspath: List<File>,
        val messageLog: String,
        val success: Boolean,
        val generatedFiles: Collection<File>,
    )

    protected open fun doValidate(): ValidationResult {
        val compilation = createKotlinCompilation()
        val result = compilation.compile()
        return ValidationResult(
            runtimeClasspath = compilation.classpaths + compilation.classesDir,
            messageLog = result.messages,
            success = result.exitCode == KotlinCompilation.ExitCode.OK,
            generatedFiles = compilation.kaptSourceDir.walk()
                .filter { it.isFile && it.extension == "java" }
                .toList(),
        )
    }

    override fun expectValidationResults(vararg messages: Message) {
        val (runtimeClasspath, messageLog, success, generatedFiles) = doValidate()
        try {
            Assert.assertFalse("No errors expected, yet compilation failed",
                messages.none { it.kind == MessageKind.Error } && !success)
            val actualMessages = parseMessages(messageLog).sorted().toList()
            val expectedMessages = messages.sorted()
            Assert.assertArrayEquals(expectedMessages.toTypedArray(), actualMessages.toTypedArray())

            if (success) {
                // find runtime test
                val classLoader = makeClassLoader(runtimeClasspath)
                try {
                    classLoader.loadClass("test.TestCaseKt").getDeclaredMethod("test").invoke(null)
                } catch (e: ClassNotFoundException) {
                    println("NOTE: No runtime test detected.")
                } catch (e: NoSuchMethodException) {
                    println("NOTE: No runtime test detected in TestCaseKt class.")
                }
            }

        } finally {
            // print generated files
            for (generatedFile in generatedFiles) {
                println("Generated file://${generatedFile.absolutePath}")
            }
        }
    }

    private fun createBaseKotlinCompilation(): KotlinCompilation {
        return KotlinCompilation().apply {
            verbose = false
            inheritClassPath = false
            javacArguments += "-Xdiags:verbose"
            classpaths = buildList {
                when (apiType) {
                    ApiType.Compiled -> CompiledApiClasspath
                    ApiType.Dynamic -> DynamicApiClasspath
                }.split(':').forEach { add(File(it)) }
                precompiledModuleOutputDir?.let { add(it) }
            }
        }
    }

    protected open fun createKotlinCompilation(): KotlinCompilation {
        return createBaseKotlinCompilation()
    }

    protected fun makeClassLoader(classpaths: List<File>): ClassLoader {
        return URLClassLoader(
            classpaths.map { it.toURI().toURL() }.toTypedArray(),
            this.javaClass.classLoader,
        )
    }

    protected enum class ApiType {
        Compiled,
        Dynamic,
    }

    private fun parseMessages(messages: String): Sequence<Message> {
        return LoggerDecorator.MessageRegex.findAll(messages).map { messageMatch ->
            val (kind, message) = messageMatch.destructured
            Message(
                kind = when (kind) {
                    "error" -> MessageKind.Error
                    "warning" -> MessageKind.Warning
                    else -> throw AssertionError()
                },
                text = message.trimIndent(),
            )
        }.memoize()
    }
}
