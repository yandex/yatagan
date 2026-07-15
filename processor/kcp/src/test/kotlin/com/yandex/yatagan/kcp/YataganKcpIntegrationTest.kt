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

package com.yandex.yatagan.kcp

import com.yandex.yatagan.Component
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.util.jar.JarFile
import javax.inject.Inject

class YataganKcpIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `plugin jar registers compiler services`() {
        val registrations = JarFile(pluginJar).use { jar ->
            expectedCompilerServices.keys.associateWith { service ->
                jar.getJarEntry("META-INF/services/$service")?.let { entry ->
                    jar.getInputStream(entry).bufferedReader().use { it.readText().trim() }
                }
            }
        }

        assertThat(registrations).containsExactlyEntriesOf(expectedCompilerServices)
    }

    @Test
    fun `plugin jar packages compiler service implementations`() {
        val entries = JarFile(pluginJar).use { jar ->
            jar.entries().asSequence().map { it.name }.toSet()
        }

        assertThat(entries).contains(
            "com/yandex/yatagan/kcp/YataganCommandLineProcessor.class",
            "com/yandex/yatagan/kcp/YataganCompilerPluginRegistrar.class",
        )
    }

    @Test
    fun `generated top-level component is discovered by Yatagan`() {
        val implementationName = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Yatagan

            @Component
            interface TestComponent

            fun test(): String {
                val component = Yatagan.create(TestComponent::class.java)
                return component.javaClass.name
            }
        """.trimIndent())

        assertThat(implementationName).isEqualTo("test.YataganTestComponent")
    }

    @Test
    fun `generated component provides a constructor-injected dependency`() {
        val dependencyName = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Yatagan
            import javax.inject.Inject

            class Dependency @Inject constructor()

            @Component
            interface TestComponent {
                fun dependency(): Dependency
            }

            fun test(): String {
                return Yatagan.create(TestComponent::class.java).dependency().javaClass.name
            }
        """.trimIndent())

        assertThat(dependencyName).isEqualTo("test.Dependency")
    }

    @Test
    fun `generated component calls an object module provision`() {
        val value = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Yatagan

            @Module
            object TestModule {
                @Provides
                fun provideAny(): Any = "provided"
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                fun any(): Any
            }

            fun test(): String {
                return Yatagan.create(TestComponent::class.java).any() as String
            }
        """.trimIndent())

        assertThat(value).isEqualTo("provided")
    }

    @Test
    fun `missing binding is reported through shared decorated diagnostics`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                class Dependency

                @Component
                interface TestComponent {
                    fun dependency(): Dependency
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(">>>[error]\n")
        assertThat(result.messages).contains("Missing binding for test.Dependency")
        assertThat(result.messages).contains("NOTE: No known way to infer the binding")
        assertThat(result.messages).doesNotContain("Unsupported KCP component")
        assertThat(result.outputDirectory.resolve("test/YataganTestComponent.class")).doesNotExist()
    }

    @Test
    fun `warnings are reported alongside validation errors`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component
                import com.yandex.yatagan.Optional

                class ConcreteDependency {
                    fun ignored(): Optional<Any> = Optional.empty()
                }

                class Missing

                @Component(dependencies = [ConcreteDependency::class])
                interface TestComponent {
                    fun missing(): Missing
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(">>>[error]\n")
        assertThat(result.messages).contains("Missing binding for test.Missing")
        assertThat(result.messages).contains(">>>[warning]\n")
        assertThat(result.messages).contains("Component dependency declaration is not abstract")
        assertThat(result.messages).contains("returns a framework type")
    }

    @Test
    fun `validation-only mode still reports missing bindings`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                class Dependency

                @Component
                interface TestComponent {
                    fun dependency(): Dependency
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
                "-P", "plugin:com.yandex.yatagan:yatagan.kcp.codegen=false",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Missing binding for test.Dependency")
        assertThat(result.outputDirectory.resolve("test/YataganTestComponent.class")).doesNotExist()
    }

    @Test
    fun `unsupported second root prevents emission for every root`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component
                import javax.inject.Inject

                class Dependency @Inject constructor()

                @Component
                interface FirstComponent {
                    fun dependency(): Dependency
                }

                @Component
                interface SecondComponent {
                    @Component.Builder
                    interface Builder {
                        fun create(): SecondComponent
                    }
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "Unsupported KCP component SecondComponent: explicit component creators are not supported",
        )
        assertThat(result.outputDirectory.resolve("test/YataganFirstComponent.class")).doesNotExist()
        assertThat(result.outputDirectory.resolve("test/YataganSecondComponent.class")).doesNotExist()
    }

    @Test
    fun `generated nested component uses runtime loader name mangling`() {
        val implementationName = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Yatagan

            class Container {
                @Component
                interface TestComponent
            }

            fun test(): String {
                val component = Yatagan.create(Container.TestComponent::class.java)
                return component.javaClass.name
            }
        """.trimIndent())

        assertThat(implementationName).isEqualTo("test.YataganContainer_TestComponent")
    }

    @Test
    fun `processor options are accepted by the compiler`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                @Component
                interface TestComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.enableStrictMode=false",
            ),
        )

        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)
    }

    @Test
    fun `invalid shared boolean option is rejected by the command line processor`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                @Component
                interface TestComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.enableStrictMode=maybe",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("invalid boolean option value: maybe")
    }

    @Test
    fun `invalid shared integer option is rejected by the command line processor`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                @Component
                interface TestComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.maxIssueEncounterPaths=many",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("invalid integer option value: many")
    }

    @Test
    fun `strict mode false keeps mandatory warnings non-fatal`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Binds
                import com.yandex.yatagan.Component
                import com.yandex.yatagan.Module
                import com.yandex.yatagan.Provides
                import javax.inject.Singleton

                @Module
                interface TestModule {
                    @Binds
                    @Singleton
                    fun number(value: Int): Number

                    companion object {
                        @Provides
                        fun integer(): Int = 0
                    }
                }

                @Component(modules = [TestModule::class])
                @Singleton
                interface TestComponent {
                    fun number(): Number
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.enableStrictMode=false",
                "-P", "plugin:com.yandex.yatagan:yatagan.kcp.codegen=false",
            ),
        )

        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)
        assertThat(result.messages).contains("Scope has no effect on 'alias' binding")
    }

    @Test
    fun `validation-only mode does not emit a component implementation`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                @Component
                interface TestComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.kcp.codegen=false",
            ),
        )

        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)
        assertThat(result.outputDirectory.resolve("test/TestComponent.class")).exists()
        assertThat(result.outputDirectory.resolve("test/YataganTestComponent.class")).doesNotExist()
    }

    private fun compileAndRun(source: String): String {
        val result = compile(source)
        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)

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

    private fun compile(
        source: String,
        additionalArguments: List<String> = emptyList(),
    ): CompilationResult {
        val sourceFile = temporaryFolder.newFile("TestCase.kt").apply {
            writeText(source)
        }
        val outputDirectory = temporaryFolder.newFolder("classes")
        val compilerOutput = ByteArrayOutputStream()
        val exitCode = PrintStream(compilerOutput).use { output ->
            K2JVMCompiler().exec(
                output,
                "-no-stdlib",
                "-no-reflect",
                "-classpath", testClasspath,
                "-jvm-target", "11",
                "-d", outputDirectory.absolutePath,
                "-Xplugin=${pluginJar.absolutePath}",
                *additionalArguments.toTypedArray(),
                sourceFile.absolutePath,
            )
        }
        val messages = compilerOutput.toString(Charsets.UTF_8.name())
        return CompilationResult(exitCode, messages, outputDirectory)
    }

    private data class CompilationResult(
        val exitCode: ExitCode,
        val messages: String,
        val outputDirectory: File,
    )

    private companion object {
        val expectedCompilerServices = mapOf(
            "org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor" to
                    "com.yandex.yatagan.kcp.YataganCommandLineProcessor",
            "org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar" to
                    "com.yandex.yatagan.kcp.YataganCompilerPluginRegistrar",
        )

        val pluginJar: File
            get() = File(checkNotNull(System.getProperty("com.yandex.yatagan.kcp.pluginJar")))

        val testClasspath: String
            get() = listOf(
                Component::class.java,
                Unit::class.java,
                Inject::class.java,
            ).map { type ->
                File(type.protectionDomain.codeSource.location.toURI()).absolutePath
            }.distinct().joinToString(File.pathSeparator)
    }
}
