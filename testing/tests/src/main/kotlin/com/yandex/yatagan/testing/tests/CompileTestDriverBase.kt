/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.testing.tests

import androidx.room.compiler.processing.util.DiagnosticMessage
import androidx.room.compiler.processing.util.compiler.TestCompilationArguments
import androidx.room.compiler.processing.util.compiler.compile
import com.yandex.yatagan.generated.CompiledApiClasspath
import com.yandex.yatagan.processor.common.BooleanOption
import com.yandex.yatagan.processor.common.IntOption
import com.yandex.yatagan.processor.common.LoggerDecorator
import com.yandex.yatagan.processor.common.Option
import com.yandex.yatagan.testing.source_set.SourceFile
import com.yandex.yatagan.testing.source_set.SourceSet
import org.junit.Assert
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

abstract class CompileTestDriverBase private constructor(
    private val apiClasspath: String,
    private val mainSourceSet: SourceSet,
) : CompileTestDriver, SourceSet by mainSourceSet {
    private var precompiledModuleOutputDirs: List<File>? = null
    private val options = mutableMapOf(
        IntOption.MaxIssueEncounterPaths.key to "100",
        IntOption.MaxSlotsPerSwitch.key to "100",
        BooleanOption.SortMethodsForTesting.key to "true",
    )

    protected constructor(
        apiClasspath: String = CompiledApiClasspath,
    ) : this(apiClasspath, SourceSet())

    override val testNameRule = TestNameRule()

    protected open val checkGoldenOutput: Boolean
        get() = true

    final override fun givenPrecompiledModule(sources: SourceSet) {
        if (precompiledModuleOutputDirs != null) {
            throw UnsupportedOperationException("Multiple precompiled modules are not supported")
        }

        val compilation = createBaseCompilationArguments().copy(
            sources = sources.sourceFiles,
        )
        val result = compile(
            workingDir = createTempDirectory(prefix = "ytc").toFile(),
            arguments = compilation,
        )
        check(result.success) {
            "Pre-compilation failed, check the code"
        }

        precompiledModuleOutputDirs = result.outputClasspath
    }

    data class TestCompilationResult(
        val workingDir: File,
        val runtimeClasspath: List<File>,
        val messageLog: String,
        val success: Boolean,
        val generatedFiles: Collection<SourceFile>,
    )

    protected open fun doCompile(): TestCompilationResult {
        val compilation = createCompilationArguments()
        val workingDir = createTempDirectory(prefix = "yct-${testNameRule.testMethodName}").toFile()
        val result = compile(
            workingDir = workingDir,
            arguments = compilation,
        )
        return TestCompilationResult(
            workingDir = workingDir,
            runtimeClasspath = compilation.classpath + result.outputClasspath,
            messageLog = result.diagnostics.values.flatten().joinToString(separator = "\n", transform = ::asString),
            success = result.success,
            generatedFiles = result.generatedSources,
        )
    }

    protected open fun runRuntimeTest(test: Method) {
        test.invoke(null)
    }

    abstract fun generatedFilesSubDir(): String?

    override fun <V : Any> givenOption(option: Option<V>, value: V) {
        options[option.key] = value.toString()
    }

    override fun compileRunAndValidate() {
        val goldenResourceBasePath = "golden/${testNameRule.testClassSimpleName}/${testNameRule.testMethodName}"
        val goldenResourcePath = "$goldenResourceBasePath.golden.txt"
        val goldenCodeResourcePath = "$goldenResourceBasePath.golden-code.txt"

        val (workingDir, runtimeClasspath, messageLog, success, generatedFiles) = doCompile()
        val strippedLog = normalizeMessages(messageLog.ensureLineEndings())

        if (goldenSourceDirForUpdate != null) {
            val goldenSourcePath = Path(goldenSourceDirForUpdate).resolve(goldenResourcePath)
            if (strippedLog.isBlank()) {
                goldenSourcePath.deleteIfExists()
            } else {
                goldenSourcePath.parent.createDirectories()
                goldenSourcePath.writeText(strippedLog)
            }
            println("Updated $goldenSourcePath")

            if (checkGoldenOutput && generatedFilesSubDir() != null) {
                val goldenCodeSourcePath = Path(goldenSourceDirForUpdate).resolve(goldenCodeResourcePath)
                if (generatedFiles.isEmpty()) {
                    goldenCodeSourcePath.deleteIfExists()
                } else {
                    goldenCodeSourcePath.parent.createDirectories()
                    goldenCodeSourcePath.writeText(buildString {
                        for (generatedFile in generatedFiles.sortedBy { it.relativePath }) {
                            appendLine(MessageSeparator)
                            append("Name: ").appendLine(generatedFile.relativePath.replace(File.separatorChar, '/'))
                            appendLine(generatedFile.contents)
                        }
                        appendLine(MessageSeparator)
                    })
                }
                println("Updated $goldenCodeResourcePath")
            }
            return
        }

        try {
            val goldenOutput = javaClass.getResourceAsStream("/$goldenResourcePath")
                ?.bufferedReader()?.readText()?.ensureLineEndings() ?: ""
            if (goldenOutput != strippedLog) {
                println("Full log:\n$messageLog")
                Assert.assertEquals(goldenOutput, strippedLog)
            }

            generatedFilesSubDir().takeIf { checkGoldenOutput }?.let {
                val goldenFiles = GoldenSourceRegex.findAll(
                    javaClass.getResourceAsStream("/$goldenCodeResourcePath")
                        ?.bufferedReader()?.readText()?.ensureLineEndings() ?: ""
                ).associateByTo(
                    destination = mutableMapOf(),
                    keySelector = { it.groupValues[1] },
                    valueTransform = { it.groupValues[2].trim() },
                )

                for (generatedFile in generatedFiles) {
                    val filePath = generatedFile.relativePath.replace(File.separatorChar, '/')
                    val goldenContents = goldenFiles.remove(filePath) ?: "<unexpected file>"
                    Assert.assertEquals("Generated file '${generatedFile.relativePath}' doesn't match the golden",
                        goldenContents, generatedFile.contents.trim())
                }
            }

            if (success) {
                // find runtime test
                val classLoader = makeClassLoader(runtimeClasspath)
                try {
                    runRuntimeTest(classLoader.loadClass("test.TestCaseKt").getDeclaredMethod("test"))
                } catch (e: ClassNotFoundException) {
                    println(runtimeClasspath)
                    println("NOTE: No runtime test detected.")
                } catch (e: NoSuchMethodException) {
                    println("NOTE: No runtime test detected in TestCaseKt class.")
                }
            } else {
                System.err.println(messageLog)
                Assert.assertTrue("Compilation failed, yet expected output is blank", goldenOutput.isNotBlank())
            }
        } finally {
            generatedFilesSubDir()?.let { generatedFilesSubDir ->
                for (generatedFile in generatedFiles) {
                    val fileUrl = "file://" + workingDir
                        .resolve(generatedFilesSubDir)
                        .resolve(generatedFile.relativePath).absolutePath
                    println("Generated $fileUrl")
                }
            }
        }
    }

    private fun createBaseCompilationArguments() = TestCompilationArguments(
        sources = sourceFiles,
        classpath = buildList {
            apiClasspath.split(File.pathSeparatorChar).forEach { add(File(it)) }
            precompiledModuleOutputDirs?.let { addAll(it) }
        },
        inheritClasspath = false,
        javacArguments = listOf(
            "-Xdiags:verbose",
            "-parameters",
        ),
        kotlincArguments = listOf(
            "-opt-in=com.yandex.yatagan.ConditionsApi",
            "-opt-in=com.yandex.yatagan.VariantApi",
            "-P", "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=true",
            "-jvm-target=11",
            "-java-parameters",
        ),
        processorOptions = options,
    )

    protected open fun createCompilationArguments() = createBaseCompilationArguments()

    protected fun makeClassLoader(classpaths: List<File>): ClassLoader {
        return URLClassLoader(
            classpaths.map { it.toURI().toURL() }.toTypedArray(),
            null,
        )
    }

    enum class ApiType {
        Compiled,
        Dynamic,
    }

    /**
     * Required to trim and sort error-messages, as they are not required to be issued in any particular order.
     */
    private fun normalizeMessages(log: String): String {
        return buildList {
            for (messageMatch in LoggerDecorator.MessageRegex.findAll(log)) {
                val (kind, message) = messageMatch.destructured
                add("$kind: ${message.trimIndent().stripColor().trim()}")
            }
            sort()
        }.joinToString(separator = "\n$MessageSeparator\n\n").trim()
    }

    companion object {
        private fun String.ensureLineEndings(): String {
            if (System.lineSeparator() == "\n") {
                return this
            }
            return replace(System.lineSeparator(), "\n")
        }

        private fun String.stripColor(): String {
            return replace(AnsiColorSequenceRegex, "")
        }

        private val AnsiColorSequenceRegex = "\u001b\\[.*?m".toRegex()

        private val MessageSeparator = "~".repeat(80)

        private val GoldenSourceRegex = """$MessageSeparator\nName: (.*?)\n(.*?)(?=$MessageSeparator)"""
            .toRegex(RegexOption.DOT_MATCHES_ALL)

        val goldenSourceDirForUpdate: String? = System.getProperty("com.yandex.yatagan.updateGoldenFiles")

        val isInUpdateGoldenMode: Boolean
            get() = goldenSourceDirForUpdate != null

        private fun asString(message: DiagnosticMessage): String = buildString {
            append('[').append(message.kind.name.lowercase()).append(']')
            append(' ')
            message.location?.let {
                append(it.source?.relativePath ?: "<unknown-source>").append(':').append(it.line).append(' ')
            }
            append(message.msg)
        }
    }
}
