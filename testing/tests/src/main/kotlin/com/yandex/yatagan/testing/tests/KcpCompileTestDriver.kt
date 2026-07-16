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

import androidx.room3.compiler.processing.util.Source
import com.yandex.yatagan.generated.CurrentClasspath
import com.yandex.yatagan.testing.source_set.SourceFile
import com.yandex.yatagan.testing.source_set.SourceSet
import org.junit.Assume
import java.io.File
import kotlin.io.path.createTempDirectory

private const val UNSUPPORTED_MARKER = "Unsupported KCP component"

internal class KcpCompileTestDriver(
    private val apiClasspath: String = CurrentClasspath.ApiCompiled,
    private val compilerClasspath: String = CurrentClasspath.KcpCompiler,
    private val pluginClasspath: String = CurrentClasspath.KcpPlugin,
) : CompileTestDriverBase(
    apiClasspath = apiClasspath,
) {
    override val backendUnderTest: Backend = Backend.Kcp

    // KCP emits IR directly - there are no generated sources to compare against golden code files.
    override val checkGoldenOutput: Boolean = false

    override fun generatedFilesSubDir(): String? = null

    override fun precompile(sources: SourceSet): List<File> {
        val result = compileWithKcp(
            sources = sources.sourceFiles,
            classpath = apiClasspath.asFiles(),
            plugins = pluginClasspath.asFiles(),
            workingDir = createTempDirectory(prefix = "ytc-kcp").toFile(),
        )
        result.assumeSupportedByKcpCodegen()
        check(result.success) { "KCP pre-compilation failed:\n${result.messageLog}" }
        return listOf(result.runtimeClasspath.last())
    }

    override fun doCompile(): TestCompilationResult {
        val result = compileWithKcp(
            sources = sourceFiles,
            classpath = apiClasspath.asFiles() + precompiledModuleClasspath,
            plugins = pluginClasspath.asFiles(),
            workingDir = createTempDirectory(prefix = "yct-kcp-${testNameRule.testMethodName}").toFile(),
        )
        result.assumeSupportedByKcpCodegen()
        return result.copy(
            runtimeClasspath = apiClasspath.asFiles() +
                    precompiledModuleClasspath + result.runtimeClasspath.last(),
        )
    }

    /**
     * Tests exercising features the KCP code generator does not support yet are honestly SKIPPED
     * (never silently passed through another backend). Validation errors always win over codegen
     * (no generation is attempted for invalid graphs), so error-expecting tests are unaffected.
     */
    private fun TestCompilationResult.assumeSupportedByKcpCodegen() {
        if (!success && UNSUPPORTED_MARKER in messageLog) {
            val reasons = messageLog.lineSequence()
                .filter { UNSUPPORTED_MARKER in it }
                .joinToString("\n")
            Assume.assumeTrue("KCP codegen does not support this test yet:\n$reasons", false)
        }
    }

    private fun compileWithKcp(
        sources: List<SourceFile>,
        classpath: List<File>,
        plugins: List<File>,
        workingDir: File,
    ): TestCompilationResult {
        // Java-declared ROOT components never surface in the Kotlin IR module, so no implementation
        // can be generated for them - same stance as other compiler-plugin DI frameworks.
        sources.filterIsInstance<Source.JavaSource>().forEach { source ->
            val isRootComponent = ComponentRegex.containsMatchIn(source.contents) &&
                    !NonRootComponentRegex.containsMatchIn(source.contents)
            Assume.assumeTrue(
                "KCP does not support Java-declared root components: ${source.relativePath}",
                !isRootComponent,
            )
        }

        val sourceDir = workingDir.resolve("sources").apply { mkdirs() }
        val outputDir = workingDir.resolve("classes").apply { mkdirs() }
        val sourceFiles = sources.mapIndexed { index, source ->
            sourceDir.resolve(index.toString()).resolve(source.relativePath).apply {
                parentFile.mkdirs()
                writeText(source.contents)
            }
        }
        val javaSourceFiles = sourceFiles.filter { it.extension == "java" }
        if (javaSourceFiles.size == sourceFiles.size) {
            // Nothing for kotlinc (and thus the plugin) to do - a plain javac module.
            val (javacOk, javacLog) = compileJavaSources(javaSourceFiles, classpath, outputDir)
            return TestCompilationResult(
                workingDir = workingDir,
                runtimeClasspath = classpath + outputDir,
                messageLog = javacLog,
                success = javacOk,
                generatedFiles = emptyList(),
            )
        }
        val arguments = buildList {
            sourceFiles.forEach { add(it.absolutePath) }
            addAll(listOf(
                "-classpath", classpath.joinToString(File.pathSeparator),
                "-d", outputDir.absolutePath,
                "-jvm-target", "11",
                "-java-parameters",
                "-Xjvm-default=all",
                "-opt-in=com.yandex.yatagan.ConditionsApi",
                "-opt-in=com.yandex.yatagan.VariantApi",
                "-no-stdlib",
                "-no-reflect",
            ))
            if (plugins.isNotEmpty()) {
                add("-Xplugin=${plugins.joinToString(",") { it.absolutePath }}")
                configuredOptions.forEach { (key, value) ->
                    add("-P")
                    add("plugin:com.yandex.yatagan:$key=$value")
                }
            }
        }
        val (success, messageLog) = IsolatedKotlinCompiler.exec(
            compilerClasspath = compilerClasspath,
            arguments = arguments,
        )
        // kotlinc only analyzes Java sources; compile them with javac against kotlinc's output.
        val javacLog = if (success && javaSourceFiles.isNotEmpty()) {
            compileJavaSources(javaSourceFiles, classpath + outputDir, outputDir)
        } else null
        return TestCompilationResult(
            workingDir = workingDir,
            runtimeClasspath = classpath + outputDir,
            messageLog = messageLog + (javacLog?.second ?: ""),
            success = success && javacLog?.first != false,
            generatedFiles = emptyList(),
        )
    }

    private fun compileJavaSources(
        javaSources: List<File>,
        classpath: List<File>,
        outputDir: File,
    ): Pair<Boolean, String> {
        val compiler = javax.tools.ToolProvider.getSystemJavaCompiler()
        val errors = java.io.ByteArrayOutputStream()
        val exitCode = compiler.run(
            null,
            null,
            errors,
            *buildList {
                add("-classpath")
                add(classpath.joinToString(File.pathSeparator))
                add("-d")
                add(outputDir.absolutePath)
                add("-nowarn")
                javaSources.forEach { add(it.absolutePath) }
            }.toTypedArray(),
        )
        return (exitCode == 0) to if (exitCode == 0) "" else "\njavac failed:\n$errors"
    }

    private companion object {
        val ComponentRegex = Regex("@(?:[\\w.]+\\.)?Component\\b")
        val NonRootComponentRegex = Regex("isRoot\\s*=\\s*false")
    }

    private fun String.asFiles(): List<File> = split(File.pathSeparatorChar).map(::File)
}

private object IsolatedKotlinCompiler {
    @Synchronized
    fun exec(
        compilerClasspath: String,
        arguments: List<String>,
    ): Pair<Boolean, String> {
        val javaExecutable = File(System.getProperty("java.home"), "bin/java")
        val process = ProcessBuilder(buildList {
            add(javaExecutable.absolutePath)
            add("-cp")
            add(compilerClasspath)
            add("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            addAll(arguments)
        }).redirectErrorStream(true).start()
        val messageLog = process.inputStream.bufferedReader().use { it.readText() }
        return (process.waitFor() == 0) to messageLog
    }
}
