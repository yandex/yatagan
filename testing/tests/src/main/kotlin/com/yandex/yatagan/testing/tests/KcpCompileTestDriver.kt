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
import java.util.Properties
import kotlin.io.path.createTempDirectory

internal class KcpCompileTestDriver(
    private val apiClasspath: String = CurrentClasspath.ApiCompiled,
    private val runtimeApiClasspath: String = CurrentClasspath.ApiDynamic,
    private val compilerClasspath: String = CurrentClasspath.KcpCompiler,
    private val pluginClasspath: String = CurrentClasspath.KcpPlugin,
) : CompileTestDriverBase(
    apiClasspath = apiClasspath,
    runtimeApiClasspath = runtimeApiClasspath,
) {
    override val backendUnderTest: Backend = Backend.Kcp
    override val checkGoldenOutput: Boolean = false

    override fun generatedFilesSubDir(): String? = null

    override fun makeClassLoader(workingDir: File, classpath: List<File>): ClassLoader {
        val resourcesDir = workingDir.resolve("kcp-runtime-resources")
        val propertiesFile = resourcesDir.resolve(
            "META-INF/com.yandex.yatagan.reflection/parameters.properties",
        )
        propertiesFile.parentFile.mkdirs()
        Properties().apply {
            configuredOptions.forEach { (key, value) ->
                when (key) {
                    "yatagan.maxIssueEncounterPaths",
                    "yatagan.enableStrictMode",
                    "yatagan.usePlainOutput",
                    "yatagan.experimental.enableDaggerCompatibility",
                    "yatagan.threadCheckerClassName" -> {
                        put(key.substringAfterLast('.'), value)
                    }
                }
            }
        }.let { properties ->
            propertiesFile.outputStream().use { properties.store(it, "Generated KCP test parameters") }
        }
        return super.makeClassLoader(workingDir, classpath + resourcesDir)
    }

    override fun precompile(sources: SourceSet): List<File> {
        val result = compileWithKcp(
            sources = sources.sourceFiles,
            classpath = apiClasspath.asFiles(),
            plugins = emptyList(),
            workingDir = createTempDirectory(prefix = "ytc-kcp").toFile(),
        )
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
        return result.copy(
            runtimeClasspath = runtimeApiClasspath.asFiles() +
                    precompiledModuleClasspath + result.runtimeClasspath.last(),
        )
    }

    private fun compileWithKcp(
        sources: List<SourceFile>,
        classpath: List<File>,
        plugins: List<File>,
        workingDir: File,
    ): TestCompilationResult {
        val javaSources = sources.filterIsInstance<Source.JavaSource>()
        Assume.assumeTrue(
            "KCP supports Kotlin sources only: ${javaSources.joinToString { it.relativePath }}",
            javaSources.isEmpty(),
        )

        val sourceDir = workingDir.resolve("sources").apply { mkdirs() }
        val outputDir = workingDir.resolve("classes").apply { mkdirs() }
        val sourceFiles = sources.mapIndexed { index, source ->
            sourceDir.resolve(index.toString()).resolve(source.relativePath).apply {
                parentFile.mkdirs()
                writeText(source.contents)
            }
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
                add("-P")
                add("plugin:com.yandex.yatagan:yatagan.kcp.codegen=false")
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
        return TestCompilationResult(
            workingDir = workingDir,
            runtimeClasspath = classpath + outputDir,
            messageLog = messageLog,
            success = success,
            generatedFiles = emptyList(),
        )
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
