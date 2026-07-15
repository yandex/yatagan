/*
 * Copyright 2026 Yandex LLC
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

@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.codegen.ir

import com.yandex.yatagan.Component
import com.yandex.yatagan.base.api.Internal
import com.yandex.yatagan.core.graph.impl.BindingGraph as buildBindingGraph
import com.yandex.yatagan.core.graph.impl.Options
import com.yandex.yatagan.core.model.impl.ComponentModel as buildComponentModel
import com.yandex.yatagan.lang.kcp.KcpLexicalScope
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.file
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import javax.inject.Inject

class IrComponentEmitterIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `graph provisions constructor dependencies and an object module method`() {
        val result = compile("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Yatagan
            import java.lang.reflect.Modifier
            import javax.inject.Inject

            class Leaf @Inject constructor() {
                val name = "leaf"
            }

            class Dependency @Inject constructor(val leaf: Leaf)

            @Module
            object TestModule {
                @Provides
                fun text(dependency: Dependency): String = dependency.leaf.name + "-provided"
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                fun dependency(): Dependency
                fun text(): String
            }

            fun test(): String {
                val component = Yatagan.create(TestComponent::class.java)
                val hasSingleConstructor = component.javaClass.declaredConstructors.size == 1
                return component.javaClass.name + ":" + component.dependency().leaf.name + ":" +
                        component.text() + ":" + hasSingleConstructor
            }
        """.trimIndent())

        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)
        assertThat(runTest(result)).isEqualTo(
            "test.YataganTestComponent:leaf:leaf-provided:true",
        )
    }

    @Test
    fun `unsupported binding is rejected before the target file is mutated`() {
        val probeFile = temporaryFolder.newFile("preflight.txt")
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                interface MissingDependency

                @Component
                interface TestComponent {
                    fun dependency(): MissingDependency
                }
            """.trimIndent(),
            preflightProbe = probeFile,
        )

        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)
        assertThat(probeFile.readText())
            .contains("UnsupportedIrGraphException")
            .contains("direct access to a missing binding")
            .contains("targetFileUnchanged=true")
        assertThat(result.outputDirectory.resolve("test/YataganTestComponent.class")).doesNotExist()
    }

    private fun compile(
        source: String,
        preflightProbe: File? = null,
    ): CompilationResult {
        val sourceFile = temporaryFolder.newFile("TestCase.kt").apply { writeText(source) }
        val outputDirectory = temporaryFolder.newFolder("classes")
        val compilerOutput = ByteArrayOutputStream()
        preflightProbe?.let { probe ->
            System.setProperty(PreflightProbeProperty, probe.absolutePath)
        }
        val exitCode = try {
            PrintStream(compilerOutput).use { output ->
                K2JVMCompiler().exec(
                    output,
                    "-no-stdlib",
                    "-no-reflect",
                    "-classpath", testClasspath,
                    "-jvm-target", "11",
                    "-d", outputDirectory.absolutePath,
                    "-Xplugin=${testPluginJar.absolutePath}",
                    sourceFile.absolutePath,
                )
            }
        } finally {
            if (preflightProbe != null) {
                System.clearProperty(PreflightProbeProperty)
            }
        }
        return CompilationResult(
            exitCode = exitCode,
            messages = compilerOutput.toString(Charsets.UTF_8.name()),
            outputDirectory = outputDirectory,
        )
    }

    private fun runTest(result: CompilationResult): String {
        val runtimeClasspath = listOf(result.outputDirectory.absolutePath) +
                testClasspath.split(File.pathSeparatorChar)
        return URLClassLoader(
            runtimeClasspath.map { File(it).toURI().toURL() }.toTypedArray(),
            null,
        ).use { classLoader ->
            classLoader.loadClass("test.TestCaseKt")
                .getDeclaredMethod("test")
                .invoke(null) as String
        }
    }

    private data class CompilationResult(
        val exitCode: ExitCode,
        val messages: String,
        val outputDirectory: File,
    )

    private companion object {
        val testPluginJar: File
            get() = File(checkNotNull(
                System.getProperty("com.yandex.yatagan.codegen.ir.testPluginJar"),
            ))

        val testClasspath: String
            get() = listOf(Component::class.java, Inject::class.java, Unit::class.java)
                .map { type -> File(type.protectionDomain.codeSource.location.toURI()).absolutePath }
                .distinct()
                .joinToString(File.pathSeparator)
    }
}

@OptIn(ExperimentalCompilerApi::class, Internal::class)
class IrCodegenTestRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "com.yandex.yatagan.codegen.ir.test"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(IrCodegenTestExtension())
    }
}

@OptIn(Internal::class)
private class IrCodegenTestExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val component = moduleFragment.files.asSequence()
            .flatMap { file -> file.declarations.asSequence() }
            .filterIsInstance<IrClass>()
            .single { declaration -> declaration.name.asString() == "TestComponent" }
        val file = component.file
        val lexicalScope = KcpLexicalScope(pluginContext, file).apply {
            ext[Options] = Options(allConditionsLazy = false)
        }
        val graph = buildBindingGraph(
            root = buildComponentModel(lexicalScope.getTypeDeclaration(component)),
        )
        val emitter = IrComponentEmitter(
            pluginContext = pluginContext,
            targetFile = file,
            graph = graph,
        )
        val probeFile = System.getProperty(PreflightProbeProperty)?.let(::File)
        if (probeFile == null) {
            emitter.emit()
            return
        }

        val declarationsBefore = file.declarations.toList()
        val failure = runCatching { emitter.emit() }.exceptionOrNull()
        val targetFileUnchanged = declarationsBefore.size == file.declarations.size &&
                declarationsBefore.zip(file.declarations).all { (before, after) -> before === after }
        probeFile.writeText(buildString {
            appendLine("failure=${failure?.javaClass?.simpleName}")
            appendLine("message=${failure?.message}")
            appendLine("targetFileUnchanged=$targetFileUnchanged")
        })
    }
}

private const val PreflightProbeProperty = "com.yandex.yatagan.codegen.ir.preflightProbe"
